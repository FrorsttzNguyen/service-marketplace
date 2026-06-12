package com.hien.marketplace.interfaces.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for creating a refund.
 *
 * REFUND TYPES:
 * - Full refund: Omit amountCents field (or set to null)
 * - Partial refund: Provide amountCents < payment amount
 *
 * VALIDATION:
 * - amountCents: if provided, must be positive
 * - reason: optional, human-readable explanation
 *
 * EXAMPLES:
 * - Full refund: {}
 * - Partial refund: {"amountCents": 5000, "reason": "Service was partially completed"}
 */
public record RefundRequest(
    @Positive(message = "Refund amount must be positive")
    Long amountCents,

    String reason
) {}
