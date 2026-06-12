package com.hien.marketplace.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho OrderStatus state machine.
 *
 * WHY test state machine:
 * - Order status phản ánh trạng thái thanh toán và fulfillment
 * - Chuyển đổi sai = business logic sai
 *
 * Order lifecycle:
 * CREATED → PENDING_PAYMENT → PAID → FULFILLED (terminal)
 *        → CANCELLED (terminal)
 *                       → REFUNDED (terminal)
 */
class OrderStatusTest {

    // === Valid transitions from CREATED ===

    @Test
    void createdCanTransitionToPendingPayment() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isTrue();
    }

    @Test
    void createdCanTransitionToCancelled() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    // === Valid transitions from PENDING_PAYMENT ===

    @Test
    void pendingPaymentCanTransitionToPaid() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
    }

    @Test
    void pendingPaymentCanTransitionToCancelled() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    // === Valid transitions from PAID ===

    @Test
    void paidCanTransitionToFulfilled() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FULFILLED)).isTrue();
    }

    @Test
    void paidCanTransitionToRefunded() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    // === Invalid transitions ===

    @Test
    void createdCannotTransitionToPaid() {
        // Phải qua PENDING_PAYMENT
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAID)).isFalse();
    }

    @Test
    void createdCannotTransitionToFulfilled() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.FULFILLED)).isFalse();
    }

    @Test
    void pendingPaymentCannotTransitionToFulfilled() {
        // Phải qua PAID
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.FULFILLED)).isFalse();
    }

    @Test
    void paidCannotTransitionToPendingPayment() {
        // Không thể rollback
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isFalse();
    }

    // === Terminal states ===

    @Test
    void fulfilledCannotTransitionToAnything() {
        assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.CREATED)).isFalse();
        assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
    }

    @Test
    void cancelledCannotTransitionToAnything() {
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CREATED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
    }

    @Test
    void refundedCannotTransitionToAnything() {
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.CREATED)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.FULFILLED)).isFalse();
    }

    // === throwIfInvalidTransition ===

    @Test
    void shouldThrowOnInvalidTransition() {
        assertThatThrownBy(() ->
            OrderStatus.FULFILLED.throwIfInvalidTransition(OrderStatus.PAID)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Cannot transition order from FULFILLED to PAID");
    }

    @Test
    void shouldNotThrowOnValidTransition() {
        assertThatCode(() ->
            OrderStatus.CREATED.throwIfInvalidTransition(OrderStatus.PENDING_PAYMENT)
        ).doesNotThrowAnyException();
    }

    // === Business scenarios ===

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        void orderCreationFlow() {
            // CREATED → PENDING_PAYMENT → PAID → FULFILLED
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isTrue();
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FULFILLED)).isTrue();
        }

        @Test
        void refundFlow() {
            // PAID → REFUNDED
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        }

        @Test
        void cancellationBeforePayment() {
            // CREATED → CANCELLED
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        void cancellationDuringPayment() {
            // PENDING_PAYMENT → CANCELLED
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid business scenarios")
    class InvalidBusinessScenarios {

        @Test
        void cannotRefundNonPaidOrder() {
            // CREATED không thể REFUNDED
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
        }

        @Test
        void cannotFulfillNonPaidOrder() {
            // CREATED không thể FULFILLED
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.FULFILLED)).isFalse();
        }

        @Test
        void cannotUnfulfillOrder() {
            // FULFILLED không thể về PAID
            assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        }
    }
}
