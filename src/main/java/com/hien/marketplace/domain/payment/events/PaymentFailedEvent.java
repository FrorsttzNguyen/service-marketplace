package com.hien.marketplace.domain.payment.events;

import com.hien.marketplace.domain.payment.Payment;

import java.time.LocalDateTime;

/**
 * Domain event fired when a payment fails (webhook payment_intent.payment_failed).
 *
 * WHY: Decouple payment failure from notification logic.
 * - PaymentService publishes event after updating status
 * - Notification listeners send failure notification to customer
 * - PaymentService doesn't know about notification details
 *
 * Benefits:
 * - Single responsibility: PaymentService focuses on payment logic
 * - Extensibility: Add retry logic, fraud detection without modifying PaymentService
 * - Async processing: Notifications don't block webhook response
 *
 * failureReason: Human-readable error from Stripe (e.g., "card_declined", "insufficient_funds")
 * failureCode: Machine-readable error code from Stripe for programmatic handling
 */
public record PaymentFailedEvent(
    Long paymentId,
    Long bookingId,
    Long customerId,
    String stripePaymentIntentId,
    String failureReason,
    String failureCode,
    LocalDateTime occurredAt
) {
    /**
     * Factory method to create event from Payment entity.
     */
    public static PaymentFailedEvent from(Payment payment, String failureReason, String failureCode) {
        return new PaymentFailedEvent(
            payment.getId(),
            payment.getBooking().getId(),
            payment.getBooking().getCustomer().getId(),
            payment.getStripePaymentIntentId(),
            failureReason,
            failureCode,
            LocalDateTime.now()
        );
    }
}
