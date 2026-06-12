package com.hien.marketplace.application.listener;

import com.hien.marketplace.application.event.BookingCancelledEvent;
import com.hien.marketplace.application.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener for customer notifications.
 *
 * WHY: Customer needs to know when their bookings are confirmed or cancelled.
 * - Email confirmation with booking details
 * - SMS reminder before appointment
 * - Cancellation notification with reason
 *
 * Separate listener from VendorNotificationListener:
 * - Different notification content
 * - Different delivery preferences (email vs SMS)
 * - Independent failure handling
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
    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Customer Notification: Booking {} confirmed for service '{}'",
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
    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Customer Notification: Booking {} cancelled for service '{}'",
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