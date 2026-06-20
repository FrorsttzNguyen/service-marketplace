package com.hien.marketplace.domain.booking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho BookingStatus state machine.
 * State Machine = business rule cốt lõi: chuyển đổi sai = bug nghiêm trọng.
 *
 * New lifecycle (after Order→Booking merge):
 *   PENDING → CONFIRMED → PAID → IN_PROGRESS → COMPLETED
 * A booking must be PAID (via Stripe webhook) before starting.
 */
class BookingStatusTest {

    // === Valid transitions ===

    @Test
    void pendingCanTransitionToConfirmed() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
    }

    @Test
    void pendingCanTransitionToCancelled() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
    }

    @Test
    void confirmedCanTransitionToPaid() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.PAID)).isTrue();
    }

    @Test
    void confirmedCanTransitionToCancelled() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
    }

    @Test
    void paidCanTransitionToInProgress() {
        assertThat(BookingStatus.PAID.canTransitionTo(BookingStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void paidCanTransitionToCancelled() {
        assertThat(BookingStatus.PAID.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
    }

    @Test
    void paidCanTransitionToRefunded() {
        assertThat(BookingStatus.PAID.canTransitionTo(BookingStatus.REFUNDED)).isTrue();
    }

    @Test
    void inProgressCanTransitionToCompleted() {
        assertThat(BookingStatus.IN_PROGRESS.canTransitionTo(BookingStatus.COMPLETED)).isTrue();
    }

    // === Invalid transitions ===

    @Test
    void confirmedCannotTransitionToInProgressDirectly() {
        // Must go through PAID first — CONFIRMED → IN_PROGRESS is no longer valid.
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void completedCannotTransitionToAnything() {
        // COMPLETED là terminal state — không thể đi đâu nữa
        assertThat(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.PENDING)).isFalse();
        assertThat(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
        assertThat(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.IN_PROGRESS)).isFalse();
        assertThat(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.CANCELLED)).isFalse();
    }

    @Test
    void cancelledCannotTransitionToAnything() {
        // CANCELLED cũng là terminal state
        assertThat(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.PENDING)).isFalse();
        assertThat(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    }

    @Test
    void refundedCannotTransitionToAnything() {
        // REFUNDED is a terminal state
        assertThat(BookingStatus.REFUNDED.canTransitionTo(BookingStatus.PENDING)).isFalse();
        assertThat(BookingStatus.REFUNDED.canTransitionTo(BookingStatus.PAID)).isFalse();
        assertThat(BookingStatus.REFUNDED.canTransitionTo(BookingStatus.COMPLETED)).isFalse();
    }

    @Test
    void pendingCannotTransitionToCompleted() {
        // Không thể bỏ qua CONFIRMED, PAID, và IN_PROGRESS
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.COMPLETED)).isFalse();
    }

    @Test
    void pendingCannotTransitionToInProgress() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.IN_PROGRESS)).isFalse();
    }

    // === throwIfInvalidTransition ===

    @Test
    void shouldThrowOnInvalidTransition() {
        assertThatThrownBy(() ->
            BookingStatus.COMPLETED.throwIfInvalidTransition(BookingStatus.PENDING)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    void shouldNotThrowOnValidTransition() {
        // Không throw = test pass
        assertThatCode(() ->
            BookingStatus.PENDING.throwIfInvalidTransition(BookingStatus.CONFIRMED)
        ).doesNotThrowAnyException();
    }
}
