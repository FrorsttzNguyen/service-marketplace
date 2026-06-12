package com.hien.marketplace.interfaces.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.domain.payment.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for refund operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundResponse(
    Long refundId,
    Long paymentId,
    String stripeRefundId,
    RefundStatus status,
    BigDecimal amount,
    String reason,
    LocalDateTime createdAt
) {
    /**
     * Factory method to create response from Refund entity.
     */
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
            refund.getId(),
            refund.getPayment().getId(),
            refund.getStripeRefundId(),
            refund.getStatus(),
            BigDecimal.valueOf(refund.getAmount().getAmountCents(), 2),
            refund.getReason(),
            refund.getCreatedAt()
        );
    }
}
