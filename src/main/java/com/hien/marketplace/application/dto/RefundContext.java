package com.hien.marketplace.application.dto;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.OrderStatus;

import java.util.List;

/**
 * Snapshot of payment data needed for refund processing.
 *
 * WHY SNAPSHOT DTO?
 * - RefundService.createRefund is NOT @Transactional (Stripe call must be outside)
 * - Accessing lazy JPA relationships outside transaction throws LazyInitializationException
 * - This DTO captures all needed data in a transactional read, then the main flow
 *   can safely access it without lazy loading issues
 *
 * DATA CAPTURED:
 * - Payment ID and Stripe ID (for Stripe API call)
 * - Payment amount (for validation and refund calculation)
 * - Order ID, status, customer ID (for authorization and business rules)
 * - Existing refund amounts (for over-refund validation)
 *
 * USED BY:
 * - RefundService.loadRefundContext() - loads data transactionally
 * - RefundService.createRefund() - uses snapshot for validation
 */
public record RefundContext(
    Long paymentId,
    String stripePaymentIntentId,
    Money paymentAmount,
    Long orderId,
    OrderStatus orderStatus,
    Long customerId,
    Money alreadyRefunded
) {
    /**
     * Calculate remaining refundable amount.
     */
    public Money getRemainingRefundable() {
        return paymentAmount.subtract(alreadyRefunded);
    }

    /**
     * Check if a user owns this payment's order.
     */
    public boolean isOwnedBy(Long userId) {
        return customerId.equals(userId);
    }

    /**
     * Check if payment is in a refundable state.
     * Only PAID orders can have their payments refunded.
     */
    public boolean isRefundable() {
        return orderStatus == OrderStatus.PAID;
    }
}
