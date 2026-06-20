package com.hien.marketplace.application.event;

import com.hien.marketplace.domain.booking.Booking;

/**
 * Domain event fired when a booking is cancelled.
 *
 * WHY: Notify both customer and provider when booking is cancelled.
 * - Different notification content for customer vs provider
 * - Potential for refund processing trigger
 */
public record BookingCancelledEvent(
    Long bookingId,
    Long customerId,
    Long providerId,
    Long serviceId,
    String serviceTitle,
    String customerName,
    String providerName,
    String reason
) {
    /**
     * Factory method to create event from Booking entity.
     */
    public static BookingCancelledEvent from(Booking booking, String reason) {
        return new BookingCancelledEvent(
            booking.getId(),
            booking.getCustomer().getId(),
            booking.getProvider().getId(),
            booking.getService().getId(),
            booking.getService().getName(),
            booking.getCustomer().getFullName(),
            booking.getProvider().getBusinessName(),
            reason
        );
    }
}