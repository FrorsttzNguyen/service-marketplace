package com.hien.marketplace.domain.payment;

import java.util.Map;
import java.util.Set;

/**
 * Enum cho trạng thái Payment — minh họa State Machine Pattern.
 *
 * State Machine = chỉ cho phép chuyển đổi giữa các trạng thái HỢP LỆ.
 * Ví dụ: PENDING → PROCESSING ✓, nhưng SUCCEEDED → PENDING ✗
 *
 * Cách hoạt động:
 * - TRANSITIONS map định nghĩa: từ trạng thái X, có thể đi đến những trạng thái nào
 * - canTransitionTo() kiểm tra xem chuyển đổi có hợp lệ không
 * - throwIfInvalidTransition() ném exception nếu không hợp lệ
 *
 * Tại sao State Machine?
 * - Ngăn bug: không ai chuyển SUCCEEDED → PENDING được (vô lý)
 * - Business rule trong code: rõ ràng, dễ test, không cần if-else rải rác
 * - Tự tài liệu: đọc TRANSITIONS map là hiểu toàn bộ lifecycle
 *
 * Payment lifecycle:
 * - PENDING: Payment mới tạo, chưa gửi đến Stripe
 * - PROCESSING: Đã tạo PaymentIntent trên Stripe, chờ customer thanh toán
 * - SUCCEEDED: Thanh toán thành công (terminal state)
 * - FAILED: Thanh toán thất bại (có thể retry → PENDING)
 */
public enum PaymentStatus {
    PENDING,        // Đang chờ xử lý (chưa tạo PaymentIntent hoặc đã tạo nhưng chưa confirm)
    PROCESSING,     // Đang xử lý qua Stripe (PaymentIntent created, awaiting payment)
    SUCCEEDED,      // Thanh toán thành công (terminal state)
    FAILED;         // Thanh toán thất bại

    // Map định nghĩa các chuyển đổi hợp lệ.
    // Key = trạng thái hiện tại, Value = set các trạng thái có thể chuyển đến.
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(PROCESSING),
        PROCESSING, Set.of(SUCCEEDED, FAILED),
        SUCCEEDED, Set.of(),  // Terminal state - không thể chuyển đi đâu nữa
        FAILED, Set.of(PENDING)  // Cho phép retry - tạo PaymentIntent mới
    );

    /**
     * Kiểm tra xem có thể chuyển từ trạng thái hiện tại sang target không.
     */
    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Validate chuyển đổi. Ném exception nếu không hợp lệ.
     * Dùng trong domain entity để enforce business rule.
     */
    public void throwIfInvalidTransition(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "Cannot transition payment from " + this + " to " + target +
                ". Allowed transitions from " + this + ": " +
                TRANSITIONS.getOrDefault(this, Set.of())
            );
        }
    }
}
