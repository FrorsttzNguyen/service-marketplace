package com.hien.marketplace.application.listener;

import com.hien.marketplace.application.event.BookingCancelledEvent;
import com.hien.marketplace.application.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener for provider notifications.
 *
 * WHY: Provider needs to know when bookings are created, confirmed, or cancelled.
 * - Email/SMS notification
 * - Dashboard updates
 * - Calendar sync
 *
 * WHY @TransactionalEventListener(phase = AFTER_COMMIT):
 * - Runs AFTER transaction commits successfully
 * - If notification fails, booking is still saved (transaction already committed)
 * - Prevents "notification failure causes booking rollback" problem
 *
 * WHY @Async:
 * - Runs in separate thread, non-blocking
 * - Main transaction thread returns immediately
 * - Notification processing doesn't slow down API response
 *
 * Phase 3 Fix: Implements async event processing as documented.
 * Previous: @EventListener (synchronous, runs during transaction)
 * Now: @TransactionalEventListener + @Async (async, runs after commit)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderNotificationListener {

    /**
     * Handle booking confirmed event.
     *
     * WHY: Provider should know when their bookings are confirmed.
     * In production: Send email/SMS notification.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("[Async] Provider Notification: Booking {} confirmed for service '{}'",
                event.bookingId(), event.serviceTitle());

        log.info("  → Provider: {} (Email: {})", event.providerName(), event.providerEmail());
        log.info("  → Customer: {}", event.customerName());

        // In production: Send notification
        // emailService.sendBookingConfirmedToProvider(
        //     event.providerEmail(),
        //     event.bookingId(),
        //     event.customerName(),
        //     event.serviceTitle()
        // );
    }

    /**
     * Handle booking cancelled event.
     *
     * WHY: Provider should know when bookings are cancelled.
     * Important for calendar management and potential refund processing.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("[Async] Provider Notification: Booking {} cancelled for service '{}'",
                event.bookingId(), event.serviceTitle());

        log.info("  → Provider: {}", event.providerName());
        log.info("  → Reason: {}", event.reason());

        // In production: Send notification
        // emailService.sendBookingCancelledToProvider(
        //     event.providerEmail(),
        //     event.bookingId(),
        //     event.reason()
        // );
    }
}
