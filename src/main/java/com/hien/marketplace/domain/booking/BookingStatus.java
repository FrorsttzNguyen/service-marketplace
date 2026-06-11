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
    CONFIRMED,      // Vendor đã xác nhận
    IN_PROGRESS,    // Đang thực hiện dịch vụ
    COMPLETED,      // Hoàn thành
    CANCELLED;      // Đã hủy

    // Map định nghĩa các chuyển đổi hợp lệ.
    // Key = trạng thái hiện tại, Value = set các trạng thái có thể chuyển đến.
    // Ví dụ: PENDING có thể chuyển sang CONFIRMED hoặc CANCELLED
    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(CONFIRMED, CANCELLED),
        CONFIRMED, Set.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, Set.of(COMPLETED)
        // COMPLETED và CANCELLED không có entry = không thể chuyển đi đâu nữa (terminal states)
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
