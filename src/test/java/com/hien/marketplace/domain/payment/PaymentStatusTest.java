package com.hien.marketplace.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho PaymentStatus state machine.
 *
 * WHY test state machine:
 * - State machine là business rule cốt lõi
 * - Chuyển đổi sai = bug nghiêm trọng (payment thất bại nhưng status sai)
 * - Test coverage cho tất cả transitions đảm bảo code đúng spec
 *
 * Payment lifecycle:
 * PENDING → PROCESSING → SUCCEEDED (terminal)
 *                   → FAILED → PENDING (retry)
 */
class PaymentStatusTest {

    // === Valid transitions ===

    @Test
    void pendingCanTransitionToProcessing() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
    }

    @Test
    void processingCanTransitionToSucceeded() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
    }

    @Test
    void processingCanTransitionToFailed() {
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void failedCanTransitionToPendingForRetry() {
        // Quan trọng: Cho phép retry payment
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING)).isTrue();
    }

    // === Invalid transitions ===

    @Test
    void succeededCannotTransitionToAnything() {
        // SUCCEEDED là terminal state — payment đã xong
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void pendingCannotTransitionToSucceeded() {
        // Không thể skip PROCESSING
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void pendingCannotTransitionToFailed() {
        // Không thể skip PROCESSING
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void processingCannotTransitionToPending() {
        // Không thể rollback
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    void failedCannotTransitionToSucceeded() {
        // Phải retry qua PENDING → PROCESSING → SUCCEEDED
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void failedCannotTransitionToProcessing() {
        // Phải reset về PENDING trước
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();
    }

    // === throwIfInvalidTransition ===

    @Test
    void shouldThrowOnInvalidTransition() {
        assertThatThrownBy(() ->
            PaymentStatus.SUCCEEDED.throwIfInvalidTransition(PaymentStatus.PENDING)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Cannot transition payment from SUCCEEDED to PENDING");
    }

    @Test
    void shouldNotThrowOnValidTransition() {
        assertThatCode(() ->
            PaymentStatus.PENDING.throwIfInvalidTransition(PaymentStatus.PROCESSING)
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnFailedToPendingRetry() {
        // Quan trọng: Retry path phải work
        assertThatCode(() ->
            PaymentStatus.FAILED.throwIfInvalidTransition(PaymentStatus.PENDING)
        ).doesNotThrowAnyException();
    }

    // === Edge cases ===

    @Nested
    @DisplayName("Terminal states")
    class TerminalStates {

        @Test
        void succeededIsTerminal() {
            // SUCCEEDED không có outgoing transitions
            assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
            assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PROCESSING)).isFalse();
            assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
            assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
        }

        @Test
        void succeededCannotTransitionToSelf() {
            assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Retry path")
    class RetryPath {

        @Test
        void failedCanRetry() {
            // FAILED → PENDING → PROCESSING → SUCCEEDED
            assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING)).isTrue();
        }

        @Test
        void afterResetToPendingCanProcess() {
            // Sau khi reset về PENDING, có thể tạo PaymentIntent mới
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
        }
    }
}
