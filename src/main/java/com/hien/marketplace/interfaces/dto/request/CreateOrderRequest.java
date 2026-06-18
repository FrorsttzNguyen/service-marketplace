package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for creating an order from a booking.
 *
 * WHY: An order is the payment-eligible aggregate built on top of a booking. The customer
 * requests an order for a booking they own; the service validates the booking is CONFIRMED,
 * computes subtotal (booking total) + commission, and persists the order.
 *
 * Validation:
 * - @NotNull + @Positive: bookingId must be present and > 0. Service/ownership/status checks
 *   happen in OrderService (they need DB lookups, so they can't be done by bean validation).
 */
public record CreateOrderRequest(

    @NotNull(message = "Booking ID is required")
    @Positive(message = "Booking ID must be positive")
    Long bookingId
) {
}
