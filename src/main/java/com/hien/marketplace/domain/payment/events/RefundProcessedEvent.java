package com.hien.marketplace.domain.payment.events;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.Refund;

import java.time.LocalDateTime;

/**
 * Domain event fired when a refund is processed successfully.
 *
 * WHY: Decouple refund processing from notification/accounting logic.
 * - RefundService publishes event after refund succeeds
 * - Notification listeners send refund confirmation to customer
 * - Accounting listeners update vendor payout records
 * - RefundService doesn't know about notification/accounting details
 *
 * Benefits:
 * - Single responsibility: RefundService focuses on refund logic
 * - Extensibility: Add new listeners without modifying RefundService
 * - Async processing: Notifications don't block refund response
 *
 * isFullRefund: true if refund amount equals payment amount, false for partial refund
 */
public record RefundProcessedEvent(
    Long refundId,
    Long paymentId,
    Long bookingId,
    Long customerId,
    String stripeRefundId,
    long refundAmountCents,
    long paymentAmountCents,
    boolean isFullRefund,
    String reason,
    LocalDateTime occurredAt
) {
    /**
     * Factory method to create event from Refund entity.
     */
    public static RefundProcessedEvent from(Refund refund) {
        Money paymentAmount = refund.getPayment().getAmount();
        return new RefundProcessedEvent(
            refund.getId(),
            refund.getPayment().getId(),
            refund.getPayment().getBooking().getId(),
            refund.getPayment().getBooking().getCustomer().getId(),
            refund.getStripeRefundId(),
            refund.getAmount().getAmountCents(),
            paymentAmount.getAmountCents(),
            refund.getAmount().equals(paymentAmount),
            refund.getReason(),
            LocalDateTime.now()
        );
    }
}
