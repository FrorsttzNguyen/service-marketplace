package com.hien.marketplace.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho RefundStatus state machine.
 *
 * WHY test state machine:
 * - State machine là business rule cốt lõi
 * - Chuyển đổi sai = bug nghiêm trọng (refund thất bại nhưng status sai)
 * - Test coverage cho tất cả transitions đảm bảo code đúng spec
 *
 * Refund lifecycle:
 * PENDING → PROCESSING → SUCCEEDED (terminal)
 *                   → FAILED → PENDING (retry)
 *
 * Consistency: Matches PaymentStatus pattern for maintainability.
 */
class RefundStatusTest {

    // === Valid transitions ===

    @Test
    void pendingCanTransitionToProcessing() {
        assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.PROCESSING)).isTrue();
    }

    @Test
    void pendingCanTransitionToSucceeded() {
        // SUCCEEDED is allowed for synchronous refunds (Stripe returns result immediately)
        assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.SUCCEEDED)).isTrue();
    }

    @Test
    void processingCanTransitionToSucceeded() {
        assertThat(RefundStatus.PROCESSING.canTransitionTo(RefundStatus.SUCCEEDED)).isTrue();
    }

    @Test
    void processingCanTransitionToFailed() {
        assertThat(RefundStatus.PROCESSING.canTransitionTo(RefundStatus.FAILED)).isTrue();
    }

    @Test
    void failedCanTransitionToPendingForRetry() {
        // Quan trọng: Cho phép retry refund
        assertThat(RefundStatus.FAILED.canTransitionTo(RefundStatus.PENDING)).isTrue();
    }

    // === Invalid transitions ===

    @Test
    void succeededCannotTransitionToAnything() {
        // SUCCEEDED là terminal state — refund đã xong
        assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.PENDING)).isFalse();
        assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.PROCESSING)).isFalse();
        assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.FAILED)).isFalse();
    }

    @Test
    void pendingCannotTransitionToFailed() {
        // Không thể skip PROCESSING for async failure
        // Sync failures are handled at Stripe API call level before Refund entity creation
        assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.FAILED)).isFalse();
    }

    @Test
    void processingCannotTransitionToPending() {
        // Không thể rollback
        assertThat(RefundStatus.PROCESSING.canTransitionTo(RefundStatus.PENDING)).isFalse();
    }

    @Test
    void failedCannotTransitionToSucceeded() {
        // Phải retry qua PENDING → PROCESSING → SUCCEEDED
        assertThat(RefundStatus.FAILED.canTransitionTo(RefundStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void failedCannotTransitionToProcessing() {
        // Phải reset về PENDING trước
        assertThat(RefundStatus.FAILED.canTransitionTo(RefundStatus.PROCESSING)).isFalse();
    }

    // === throwIfInvalidTransition ===

    @Test
    void shouldThrowOnInvalidTransition() {
        assertThatThrownBy(() ->
            RefundStatus.SUCCEEDED.throwIfInvalidTransition(RefundStatus.PENDING)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Cannot transition refund from SUCCEEDED to PENDING");
    }

    @Test
    void shouldNotThrowOnValidTransition() {
        assertThatCode(() ->
            RefundStatus.PENDING.throwIfInvalidTransition(RefundStatus.PROCESSING)
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnFailedToPendingRetry() {
        // Quan trọng: Retry path phải work
        assertThatCode(() ->
            RefundStatus.FAILED.throwIfInvalidTransition(RefundStatus.PENDING)
        ).doesNotThrowAnyException();
    }

    // === Edge cases ===

    @Nested
    @DisplayName("Terminal states")
    class TerminalStates {

        @Test
        void succeededIsTerminal() {
            // SUCCEEDED không có outgoing transitions
            assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.PENDING)).isFalse();
            assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.PROCESSING)).isFalse();
            assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.FAILED)).isFalse();
            assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.SUCCEEDED)).isFalse();
        }

        @Test
        void succeededCannotTransitionToSelf() {
            assertThat(RefundStatus.SUCCEEDED.canTransitionTo(RefundStatus.SUCCEEDED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Retry path")
    class RetryPath {

        @Test
        void failedCanRetry() {
            // FAILED → PENDING → PROCESSING → SUCCEEDED
            assertThat(RefundStatus.FAILED.canTransitionTo(RefundStatus.PENDING)).isTrue();
        }

        @Test
        void afterResetToPendingCanProcess() {
            // Sau khi reset về PENDING, có thể gửi refund request mới
            assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.PROCESSING)).isTrue();
        }
    }

    // === Self-transitions ===

    @Test
    void pendingCannotTransitionToSelf() {
        assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.PENDING)).isFalse();
    }

    @Test
    void processingCannotTransitionToSelf() {
        assertThat(RefundStatus.PROCESSING.canTransitionTo(RefundStatus.PROCESSING)).isFalse();
    }

    @Test
    void failedCannotTransitionToSelf() {
        assertThat(RefundStatus.FAILED.canTransitionTo(RefundStatus.FAILED)).isFalse();
    }
}