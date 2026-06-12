package com.hien.marketplace.interfaces.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for creating a payment.
 *
 * WHY use DTOs:
 * - Isolate API contract from domain entities
 * - Validation annotations on DTO, not entity
 * - Security: prevent over-posting (only allowed fields)
 * - Flexibility: API can change without affecting domain
 *
 * Validation:
 * - orderId: required, must be positive
 * - paymentMethod: required, non-blank (e.g., "card")
 */
public record PaymentCreateRequest(
    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    Long orderId,

    @NotBlank(message = "Payment method is required")
    String paymentMethod
) {}
