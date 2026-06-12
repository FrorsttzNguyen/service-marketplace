package com.hien.marketplace.interfaces.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for payment operations.
 *
 * WHY use @JsonInclude.Include.NON_NULL:
 * - Excludes null fields from JSON response
 * - Cleaner API response
 * - Client doesn't see "field: null"
 *
 * clientSecret: Used by Stripe.js on frontend to confirm payment
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
    Long paymentId,
    String stripePaymentIntentId,
    String clientSecret,
    PaymentStatus status,
    Long orderId,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    LocalDateTime createdAt
) {
    /**
     * Factory method to create response from Payment entity.
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getStripePaymentIntentId(),
            null, // clientSecret only available when creating PaymentIntent
            payment.getStatus(),
            payment.getOrder().getId(),
            BigDecimal.valueOf(payment.getAmount().getAmountCents(), 2),
            "USD",
            payment.getPaymentMethod(),
            payment.getCreatedAt()
        );
    }

    /**
     * Factory method for create response with client secret.
     */
    public static PaymentResponse withClientSecret(Payment payment, String clientSecret) {
        return new PaymentResponse(
            payment.getId(),
            payment.getStripePaymentIntentId(),
            clientSecret,
            payment.getStatus(),
            payment.getOrder().getId(),
            BigDecimal.valueOf(payment.getAmount().getAmountCents(), 2),
            "USD",
            payment.getPaymentMethod(),
            payment.getCreatedAt()
        );
    }
}
