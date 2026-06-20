-- =============================================================================
-- V12: Merge Order into Booking
--
-- Đường 1 refactor: bỏ Order aggregate. Booking trở thành aggregate root duy nhất,
-- Payment tham chiếu thẳng Booking. Một state machine duy nhất cho cả vòng đời
-- đặt lịch + thanh toán.
--
--   TRƯỚC: Service → Booking (lifecycle A) → Order (lifecycle B) → Payment → Refund
--   SAU:   Service → Booking (1 lifecycle) → Payment → Refund
--
-- Tiền dời từ orders lên bookings (subtotal/commission/total). Payment.order_id → booking_id.
-- Migration này MIGRATE data hiện có (backfill từ orders) rồi mới DROP TABLE orders —
-- không drop trắng.
--
-- LƯU Ý: test chạy trên H2 với ddl-auto=create-drop + Flyway TẮT, nên file này chỉ chạy
-- trên Postgres thật. Schema test dựng từ entity, không từ migration.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Booking money: total_price_cents (= subtotal) → subtotal_cents, thêm commission + total
-- -----------------------------------------------------------------------------
ALTER TABLE bookings RENAME COLUMN total_price_cents TO subtotal_cents;
ALTER TABLE bookings ADD COLUMN commission_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN total_cents      BIGINT NOT NULL DEFAULT 0;

-- Backfill cho booking ĐÃ có order: lấy nguyên số tiền từ order (nguồn sự thật cũ).
UPDATE bookings b
SET subtotal_cents   = o.subtotal_cents,
    commission_cents = o.commission_cents,
    total_cents      = o.total_cents
FROM orders o
WHERE o.booking_id = b.id;

-- Backfill cho booking CHƯA có order: tự suy ra commission = round(subtotal × 10%).
-- 10% = default app.commission.rate (CommissionProperties). HALF_UP qua ROUND.
UPDATE bookings b
SET commission_cents = ROUND(b.subtotal_cents * 0.10),
    total_cents      = b.subtotal_cents + ROUND(b.subtotal_cents * 0.10)
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.booking_id = b.id);

-- Data đã đầy → bỏ DEFAULT (giữ NOT NULL). Từ giờ app luôn set tường minh khi tạo booking.
ALTER TABLE bookings ALTER COLUMN commission_cents DROP DEFAULT;
ALTER TABLE bookings ALTER COLUMN total_cents      DROP DEFAULT;

-- Mở rộng CHECK status: thêm PAID, REFUNDED (gộp từ Order lifecycle).
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
    CHECK (status IN ('PENDING','CONFIRMED','PAID','IN_PROGRESS','COMPLETED','CANCELLED','REFUNDED'));

-- -----------------------------------------------------------------------------
-- 2. Payments: order_id → booking_id
-- -----------------------------------------------------------------------------
ALTER TABLE payments ADD COLUMN booking_id BIGINT;

-- Backfill: payment.order_id → order.booking_id.
UPDATE payments p
SET booking_id = o.booking_id
FROM orders o
WHERE p.order_id = o.id;

ALTER TABLE payments ALTER COLUMN booking_id SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT fk_payments_booking
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE;

-- Một payment / một booking (thay cho unique cũ trên order_id ở V9).
ALTER TABLE payments ADD CONSTRAINT uq_payments_booking_id UNIQUE (booking_id);
CREATE INDEX idx_payments_booking_id ON payments(booking_id);

-- Bỏ cột order_id. Postgres tự drop kèm uq_payments_order_id (V9) và idx_payments_order_id (V5)
-- vì chúng chỉ phụ thuộc đúng cột này.
ALTER TABLE payments DROP COLUMN order_id;

-- -----------------------------------------------------------------------------
-- 3. Xóa bảng orders — không còn ai tham chiếu.
-- -----------------------------------------------------------------------------
DROP TABLE orders;
