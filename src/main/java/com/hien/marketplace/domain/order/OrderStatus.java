package com.hien.marketplace.domain.order;

import java.util.Map;
import java.util.Set;

/**
 * Enum cho trạng thái Order — minh họa State Machine Pattern.
 *
 * State Machine = chỉ cho phép chuyển đổi giữa các trạng thái HỢP LỆ.
 * Ví dụ: CREATED → PAID ✓, nhưng FULFILLED → CREATED ✗
 *
 * Cách hoạt động:
 * - TRANSITIONS map định nghĩa: từ trạng thái X, có thể đi đến những trạng thái nào
 * - canTransitionTo() kiểm tra xem chuyển đổi có hợp lệ không
 * - throwIfInvalidTransition() ném exception nếu không hợp lệ
 *
 * Tại sao State Machine?
 * - Ngăn bug: không ai chuyển FULFILLED → CREATED được (vô lý)
 * - Business rule trong code: rõ ràng, dễ test, không cần if-else rải rác
 * - Tự tài liệu: đọc TRANSITIONS map là hiểu toàn bộ lifecycle
 *
 * Order lifecycle:
 * - CREATED: Order mới tạo từ Booking, chưa thanh toán
 * - PENDING_PAYMENT: Customer đang trong quá trình thanh toán (PaymentIntent created)
 * - PAID: Thanh toán thành công
 * - FULFILLED: Dịch vụ đã hoàn thành (vendor delivered)
 * - CANCELLED: Order đã hủy (trước hoặc sau khi thanh toán)
 * - REFUNDED: Đã hoàn tiền (full refund)
 */
public enum OrderStatus {
    CREATED,            // Vừa tạo, chưa thanh toán
    PENDING_PAYMENT,    // Đang chờ thanh toán (PaymentIntent created)
    PAID,               // Đã thanh toán thành công
    FULFILLED,          // Đã hoàn thành dịch vụ
    CANCELLED,          // Đã hủy
    REFUNDED;           // Đã hoàn tiền

    // Map định nghĩa các chuyển đổi hợp lệ.
    // Key = trạng thái hiện tại, Value = set các trạng thái có thể chuyển đến.
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        CREATED, Set.of(PENDING_PAYMENT, CANCELLED),
        PENDING_PAYMENT, Set.of(PAID, CANCELLED),
        PAID, Set.of(FULFILLED, REFUNDED),
        FULFILLED, Set.of(),    // Terminal state
        CANCELLED, Set.of(),    // Terminal state
        REFUNDED, Set.of()      // Terminal state
    );

    /**
     * Kiểm tra xem có thể chuyển từ trạng thái hiện tại sang target không.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Validate chuyển đổi. Ném exception nếu không hợp lệ.
     * Dùng trong domain entity để enforce business rule.
     */
    public void throwIfInvalidTransition(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "Cannot transition order from " + this + " to " + target +
                ". Allowed transitions from " + this + ": " +
                TRANSITIONS.getOrDefault(this, Set.of())
            );
        }
    }
}