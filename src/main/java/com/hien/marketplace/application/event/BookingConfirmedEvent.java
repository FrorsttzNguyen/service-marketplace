package com.hien.marketplace.application.event;

import com.hien.marketplace.domain.booking.Booking;

/**
 * Domain event fired when a booking is confirmed by provider.
 *
 * WHY: Decouple booking confirmation from notification logic.
 * - BookingService publishes event
 * - Notification listeners handle sending emails/SMS
 * - BookingService doesn't know about notification details
 *
 * Benefits:
 * - Single responsibility: BookingService focuses on booking logic
 * - Extensibility: Add new listeners without modifying BookingService
 * - Async processing: Notification doesn't block booking confirmation
 *
 * Using record for immutable event data.
 */
public record BookingConfirmedEvent(
    Long bookingId,
    Long customerId,
    Long providerId,
    Long serviceId,
    String serviceTitle,
    String customerName,
    String providerName,
    String customerEmail,
    String providerEmail
) {
    /**
     * Factory method to create event from Booking entity.
     *
     * WHY: Extracts all needed info in one place.
     * Listeners receive complete context without additional queries.
     */
    public static BookingConfirmedEvent from(Booking booking) {
        return new BookingConfirmedEvent(
            booking.getId(),
            booking.getCustomer().getId(),
            booking.getProvider().getId(),
            booking.getService().getId(),
            booking.getService().getName(),
            booking.getCustomer().getFullName(),
            booking.getProvider().getBusinessName(),
            booking.getCustomer().getEmail(),
            booking.getProvider().getUser().getEmail()
        );
    }
}