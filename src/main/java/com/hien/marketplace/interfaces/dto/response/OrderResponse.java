package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Order entity.
 *
 * WHY: Returns order details with payment info.
 * Customer and vendor use this to track order status.
 *
 * Note: Includes paymentId for Stripe integration (Phase 4).
 */
public record OrderResponse(
    Long id,
    Long bookingId,
    Long customerId,
    Long vendorId,
    BigDecimal totalAmount,
    String currency,
    OrderStatus status,
    String paymentMethod,
    String paymentId,  // Stripe PaymentIntent ID
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}