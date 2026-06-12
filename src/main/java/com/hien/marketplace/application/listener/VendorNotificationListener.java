package com.hien.marketplace.application.listener;

import com.hien.marketplace.application.event.BookingCancelledEvent;
import com.hien.marketplace.application.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener for vendor notifications.
 *
 * WHY: Vendor needs to know when bookings are created, confirmed, or cancelled.
 * - Email/SMS notification
 * - Dashboard updates
 * - Calendar sync
 *
 * WHY @EventListener: Simple synchronous event handling.
 * - Runs in same thread as event publisher
 * - Good for lightweight operations like logging
 *
 * For production: Use @TransactionalEventListener for async processing:
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * - Runs after transaction commits
 * - Notification doesn't affect transaction rollback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VendorNotificationListener {

    /**
     * Handle booking confirmed event.
     *
     * WHY: Vendor should know when their bookings are confirmed.
     * In production: Send email/SMS notification.
     */
    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Vendor Notification: Booking {} confirmed for service '{}'",
                event.bookingId(), event.serviceTitle());

        log.info("  → Vendor: {} (Email: {})", event.vendorName(), event.vendorEmail());
        log.info("  → Customer: {}", event.customerName());

        // In production: Send notification
        // emailService.sendBookingConfirmedToVendor(
        //     event.vendorEmail(),
        //     event.bookingId(),
        //     event.customerName(),
        //     event.serviceTitle()
        // );
    }

    /**
     * Handle booking cancelled event.
     *
     * WHY: Vendor should know when bookings are cancelled.
     * Important for calendar management and potential refund processing.
     */
    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Vendor Notification: Booking {} cancelled for service '{}'",
                event.bookingId(), event.serviceTitle());

        log.info("  → Vendor: {}", event.vendorName());
        log.info("  → Reason: {}", event.reason());

        // In production: Send notification
        // emailService.sendBookingCancelledToVendor(
        //     event.vendorEmail(),
        //     event.bookingId(),
        //     event.reason()
        // );
    }
}