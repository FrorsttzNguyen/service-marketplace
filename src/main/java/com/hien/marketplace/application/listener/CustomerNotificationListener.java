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
 * Listener for customer notifications.
 *
 * WHY: Customer needs to know when their bookings are confirmed or cancelled.
 * - Email confirmation with booking details
 * - SMS reminder before appointment
 * - Cancellation notification with reason
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
 * Separate listener from VendorNotificationListener:
 * - Different notification content
 * - Different delivery preferences (email vs SMS)
 * - Independent failure handling
 *
 * Phase 3 Fix: Implements async event processing as documented.
 * Previous: @EventListener (synchronous, runs during transaction)
 * Now: @TransactionalEventListener + @Async (async, runs after commit)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNotificationListener {

    /**
     * Handle booking confirmed event.
     *
     * WHY: Customer should receive confirmation with booking details.
     * Include: Service name, time, vendor contact, cancellation policy.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("[Async] Customer Notification: Booking {} confirmed for service '{}'",
                event.bookingId(), event.serviceTitle());

        log.info("  → Customer: {} (Email: {})", event.customerName(), event.customerEmail());
        log.info("  → Vendor: {}", event.vendorName());

        // In production: Send notification
        // emailService.sendBookingConfirmedToCustomer(
        //     event.customerEmail(),
        //     event.bookingId(),
        //     event.serviceTitle(),
        //     event.vendorName()
        // );
    }

    /**
     * Handle booking cancelled event.
     *
     * WHY: Customer should receive cancellation confirmation.
     * Include: Reason, refund status if applicable, rebooking options.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("[Async] Customer Notification: Booking {} cancelled for service '{}'",
                event.bookingId(), event.serviceTitle());

        log.info("  → Customer: {}", event.customerName());
        log.info("  → Reason: {}", event.reason());

        // In production: Send notification
        // emailService.sendBookingCancelledToCustomer(
        //     event.customerEmail(),
        //     event.bookingId(),
        //     event.reason()
        // );
    }
}
