package com.hien.marketplace.application.event;

import com.hien.marketplace.domain.booking.Booking;

/**
 * Domain event fired when a booking is cancelled.
 *
 * WHY: Notify both customer and vendor when booking is cancelled.
 * - Different notification content for customer vs vendor
 * - Potential for refund processing trigger
 */
public record BookingCancelledEvent(
    Long bookingId,
    Long customerId,
    Long vendorId,
    Long serviceId,
    String serviceTitle,
    String customerName,
    String vendorName,
    String reason
) {
    /**
     * Factory method to create event from Booking entity.
     */
    public static BookingCancelledEvent from(Booking booking, String reason) {
        return new BookingCancelledEvent(
            booking.getId(),
            booking.getCustomer().getId(),
            booking.getVendor().getId(),
            booking.getService().getId(),
            booking.getService().getName(),
            booking.getCustomer().getFullName(),
            booking.getVendor().getBusinessName(),
            reason
        );
    }
}