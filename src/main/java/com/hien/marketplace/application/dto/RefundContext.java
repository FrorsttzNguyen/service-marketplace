package com.hien.marketplace.application.dto;

import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.PaymentStatus;

/**
 * Snapshot of payment data needed for refund processing.
 *
 * WHY SNAPSHOT DTO?
 * - RefundService.createRefund is NOT @Transactional (Stripe call must be outside)
 * - Accessing lazy JPA relationships outside transaction throws LazyInitializationException
 * - This DTO captures all needed data in a transactional read, then the main flow
 *   can safely access it without lazy loading issues
 *
 * WHY LOAD FROM SEPARATE BEAN?
 * - Spring @Transactional uses proxy-based AOP
 * - Self-invocation (this.method()) bypasses the proxy
 * - @Transactional on loadRefundContext would be ignored if called from same class
 * - Moving to RefundTransactionService ensures proxy intercepts the call
 *
 * DATA CAPTURED:
 * - Payment ID, Stripe ID, and status (for Stripe API call and validation)
 * - Payment amount (for validation and refund calculation)
 * - Booking ID, status, customer ID (for authorization and business rules)
 * - Existing refund amounts (for over-refund validation)
 *
 * USED BY:
 * - RefundTransactionService.loadRefundContext() - loads data transactionally
 * - RefundService.createRefund() - uses snapshot for validation
 */
public record RefundContext(
    Long paymentId,
    String stripePaymentIntentId,
    PaymentStatus paymentStatus,
    Money paymentAmount,
    Long bookingId,
    BookingStatus bookingStatus,
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
     * Check if a user owns this payment's booking.
     */
    public boolean isOwnedBy(Long userId) {
        return customerId.equals(userId);
    }

    /**
     * Check if payment is in a refundable state.
     * REQUIREMENTS:
     * - Booking must be PAID (the booking is paid)
     * - Payment must be SUCCEEDED (money was actually received)
     */
    public boolean isRefundable() {
        return bookingStatus == BookingStatus.PAID && paymentStatus == PaymentStatus.SUCCEEDED;
    }
}
