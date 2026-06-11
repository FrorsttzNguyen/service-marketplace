 -- =============================================================================
-- V4: Bookings & Booking Status History
-- The most critical table. Double-booking prevented by TWO layers:
--   Layer 1: Optimistic locking (version column) - application level
--   Layer 2: UNIQUE constraint (service_id, booking_date, start_time) - DB level
-- =============================================================================

CREATE TABLE bookings (
    id                  BIGSERIAL       PRIMARY KEY,
    service_id          BIGINT          NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    customer_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vendor_id           BIGINT          NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    booking_date        DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    total_price_cents   BIGINT          NOT NULL,
    notes               TEXT,
    version             BIGINT          NOT NULL DEFAULT 0,  -- optimistic lock
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- Layer 2: database-level double-booking prevention
    CONSTRAINT uq_booking_slot UNIQUE (service_id, booking_date, start_time),
    CONSTRAINT chk_booking_time CHECK (start_time < end_time)
);

-- Index: vendor schedule - view bookings by vendor and date
CREATE INDEX idx_bookings_vendor_date ON bookings(vendor_id, booking_date);

-- Index: customer booking history
CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);

-- Index: find bookings by status (e.g., all PENDING for admin)
CREATE INDEX idx_bookings_status ON bookings(status);

CREATE TABLE booking_status_history (
    id              BIGSERIAL       PRIMARY KEY,
    booking_id      BIGINT          NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)     NOT NULL,
    changed_by      BIGINT          REFERENCES users(id),
    changed_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    reason          TEXT
);

CREATE INDEX idx_booking_status_history_booking_id ON booking_status_history(booking_id);
