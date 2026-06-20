package com.hien.marketplace.domain.payment.events;

import com.hien.marketplace.domain.payment.Payment;

import java.time.LocalDateTime;

/**
 * Domain event fired when a payment succeeds (webhook payment_intent.succeeded).
 *
 * WHY: Decouple payment success from booking/notification logic.
 * - PaymentService publishes event after updating status
 * - Notification listeners send confirmation emails
 * - PaymentService doesn't know about notification details
 *
 * Benefits:
 * - Single responsibility: PaymentService focuses on payment logic
 * - Extensibility: Add new listeners (loyalty, analytics) without modifying PaymentService
 * - Async processing: Notifications don't block webhook response (must return 200 quickly)
 *
 * Using record for immutable event data.
 *
 * Tại sao dùng record?
 * - Immutability: Event data không thay đổi sau khi tạo
 * - Concise: Không cần boilerplate constructor, getter, equals, hashCode
 * - Thread-safe: Immutable objects are inherently thread-safe
 */
public record PaymentSucceededEvent(
    Long paymentId,
    Long bookingId,
    Long customerId,
    Long vendorId,
    String stripePaymentIntentId,
    long amountCents,
    LocalDateTime occurredAt
) {
    /**
     * Factory method to create event from Payment entity.
     *
     * WHY: Extracts all needed info in one place.
     * Listeners receive complete context without additional queries.
     */
    public static PaymentSucceededEvent from(Payment payment) {
        return new PaymentSucceededEvent(
            payment.getId(),
            payment.getBooking().getId(),
            payment.getBooking().getCustomer().getId(),
            payment.getBooking().getVendor().getId(),
            payment.getStripePaymentIntentId(),
            payment.getAmount().getAmountCents(),
            LocalDateTime.now()
        );
    }
}
