package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating an order from a booking.
 *
 * WHY: Order is created after booking is confirmed.
 * Order links to payment (Stripe) and tracks payment status.
 *
 * Note: Booking must be in CONFIRMED status before order can be created.
 * This is validated in service layer.
 */
public record OrderCreateRequest(
    @NotNull(message = "Booking ID is required")
    Long bookingId,

    String paymentMethod  // e.g., "STRIPE", "VNPAY" - defaults to STRIPE
) {
}