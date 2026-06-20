package com.hien.marketplace.domain.booking;

import java.util.Map;
import java.util.Set;

/**
 * Enum cho trạng thái Booking — minh họa State Machine Pattern.
 *
 * State Machine = chỉ cho phép chuyển đổi giữa các trạng thái HỢP LỆ.
 * Ví dụ: PENDING → CONFIRMED ✓, nhưng COMPLETED → PENDING ✗
 *
 * Cách hoạt động:
 * - TRANSITIONS map định nghĩa: từ trạng thái X, có thể đi đến những trạng thái nào
 * - canTransitionTo() kiểm tra xem chuyển đổi có hợp lệ không
 * - throwIfInvalidTransition() ném exception nếu không hợp lệ
 *
 * Tại sao State Machine?
 * - Ngăn bug: không ai chuyển COMPLETED → PENDING được (vô lý)
 * - Business rule trong code: rõ ràng, dễ test, không cần if-else rải rác
 * - Tự tài liệu: đọc TRANSITIONS map là hiểu toàn bộ lifecycle
 */
public enum BookingStatus {
    PENDING,        // Customer vừa đặt, chờ vendor xác nhận
    CONFIRMED,      // Vendor đã xác nhận — giờ là trạng thái "payable"
    PAID,           // Thanh toán thành công (webhook Stripe) — gộp từ Order.PAID cũ
    IN_PROGRESS,    // Đang thực hiện dịch vụ
    COMPLETED,      // Hoàn thành (nguồn sự thật duy nhất cho "xong" — thay cho Order.FULFILLED)
    CANCELLED,      // Đã hủy
    REFUNDED;       // Đã hoàn tiền — gộp từ Order.REFUNDED cũ

    // Map định nghĩa các chuyển đổi hợp lệ.
    // Key = trạng thái hiện tại, Value = set các trạng thái có thể chuyển đến.
    //
    // Vòng đời gộp (Đường 1): đặt → vendor nhận → trả tiền → làm dịch vụ → xong.
    //   PENDING → CONFIRMED → PAID → IN_PROGRESS → COMPLETED
    // "Đang trả tiền" KHÔNG phải một state ở đây — Booking vẫn CONFIRMED, còn Payment có
    // lifecycle riêng (PENDING/PROCESSING) cho giao dịch Stripe. Đó không phải trùng lặp.
    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(CONFIRMED, CANCELLED),
        CONFIRMED, Set.of(PAID, CANCELLED),
        PAID, Set.of(IN_PROGRESS, CANCELLED, REFUNDED),
        IN_PROGRESS, Set.of(COMPLETED)
        // COMPLETED, CANCELLED, REFUNDED không có entry = terminal states.
    );

    /**
     * Kiểm tra xem có thể chuyển từ trạng thái hiện tại sang target không.
     */
    public boolean canTransitionTo(BookingStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Validate chuyển đổi. Ném exception nếu không hợp lệ.
     * Dùng trong domain entity để enforce business rule.
     */
    public void throwIfInvalidTransition(BookingStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "Cannot transition booking from " + this + " to " + target +
                ". Allowed transitions from " + this + ": " +
                TRANSITIONS.getOrDefault(this, Set.of())
            );
        }
    }
}
