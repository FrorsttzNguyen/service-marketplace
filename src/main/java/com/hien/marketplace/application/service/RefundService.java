package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.*;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import com.hien.marketplace.domain.payment.RefundStatus;
import com.hien.marketplace.domain.payment.events.RefundProcessedEvent;
import com.hien.marketplace.infrastructure.persistence.PaymentRepository;
import com.hien.marketplace.infrastructure.persistence.RefundRepository;
import com.hien.marketplace.infrastructure.stripe.StripeClient;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for refund operations.
 *
 * ARCHITECTURE PATTERN:
 * - Stripe API calls OUTSIDE @Transactional
 * - Database operations INSIDE @Transactional (via RefundTransactionService)
 *
 * REFUND TYPES:
 * - Full refund: Refund entire payment amount
 * - Partial refund: Refund part of payment amount
 *
 * REFUND VALIDATION:
 * - Only SUCCEEDED payments can be refunded
 * - Total refunds cannot exceed payment amount
 * - Partial refunds must have positive amount
 *
 * TRANSACTION SEMANTICS:
 * - Self-invocation of @Transactional methods doesn't work (Spring proxy limitation)
 * - DB mutations moved to RefundTransactionService for proper transaction boundary
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final StripeClient stripeClient;
    private final ApplicationEventPublisher eventPublisher;
    private final RefundTransactionService refundTransactionService;

    /**
     * Create a refund for a payment.
     *
     * FLOW:
     * 1. Validate payment ownership and status
     * 2. Validate refund amount
     * 3. Create Stripe Refund (OUTSIDE transaction)
     * 4. Create local Refund entity (INSIDE transaction)
     * 5. Update Order status if full refund
     * 6. Publish RefundProcessedEvent
     *
     * @param userId Current user ID (for authorization)
     * @param paymentId Payment to refund
     * @param amountCents Refund amount in cents (null for full refund)
     * @param reason Reason for refund
     * @return Created Refund entity
     */
    public com.hien.marketplace.domain.payment.Refund createRefund(Long userId, Long paymentId, Long amountCents, String reason) {
        log.info("Creating refund for payment {} by user {}, amount={}",
                paymentId, userId, amountCents != null ? amountCents : "full");

        // Step 1: Validate payment
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // Authorization: only order owner can request refund
        if (!payment.getOrder().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Refund", "You can only refund your own payments");
        }

        // Business rule: only succeeded payments can be refunded
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentException(
                    "Payment status",
                    "Only succeeded payments can be refunded. Current status: " + payment.getStatus(),
                    payment.getStatus()
            );
        }

        // Determine refund amount
        Money refundAmount = amountCents != null
                ? Money.of(amountCents)
                : payment.getAmount(); // Full refund

        // Validate refund amount
        validateRefundAmount(payment, refundAmount);

        // Step 2: Create Stripe Refund (OUTSIDE transaction)
        com.stripe.model.Refund stripeRefund;
        try {
            stripeRefund = stripeClient.createRefund(
                    payment.getStripePaymentIntentId(),
                    refundAmount.equals(payment.getAmount()) ? null : refundAmount.getAmountCents()
            );
            log.info("Created Stripe Refund: {}", stripeRefund.getId());
        } catch (StripeException e) {
            log.error("Failed to create refund for payment {}: {}", paymentId, e.getMessage());
            throw new StripeApiException("Failed to create refund: " + e.getMessage(), e);
        }

        // Step 3: Create local Refund (INSIDE transaction)
        // Uses RefundTransactionService for proper transaction boundary via Spring proxy
        com.hien.marketplace.domain.payment.Refund refund = refundTransactionService.createRefundWithOrderUpdate(
                payment, refundAmount, reason, stripeRefund.getId());

        log.info("Refund created successfully: refundId={}, stripeRefundId={}",
                refund.getId(), stripeRefund.getId());

        return refund;
    }

    /**
     * Validate refund amount.
     *
     * Rules:
     * - Refund amount must be positive
     * - Total refunds cannot exceed payment amount
     */
    private void validateRefundAmount(Payment payment, Money refundAmount) {
        // Refund amount must be positive
        if (refundAmount.getAmountCents() <= 0) {
            throw new PaymentException("Refund amount", "Refund amount must be positive");
        }

        // Calculate already refunded amount
        Money alreadyRefunded = calculateTotalRefunded(payment);

        // Total refunds cannot exceed payment
        Money totalAfterRefund = alreadyRefunded.add(refundAmount);
        if (totalAfterRefund.getAmountCents() > payment.getAmount().getAmountCents()) {
            throw new PaymentException(
                    "Refund amount",
                    String.format("Total refunds (%.2f) cannot exceed payment amount (%.2f)",
                            toDecimal(totalAfterRefund), toDecimal(payment.getAmount()))
            );
        }
    }

    /**
     * Calculate total amount already refunded for a payment.
     */
    private Money calculateTotalRefunded(Payment payment) {
        return payment.getRefunds().stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
                .map(com.hien.marketplace.domain.payment.Refund::getAmount)
                .reduce(Money.of(0), Money::add);
    }

    /**
     * Convert Money to BigDecimal for display.
     */
    private BigDecimal toDecimal(Money money) {
        return BigDecimal.valueOf(money.getAmountCents(), 2);
    }

    /**
     * Get refund by ID.
     */
    @Transactional(readOnly = true)
    public com.hien.marketplace.domain.payment.Refund getRefund(Long userId, Long refundId) {
        com.hien.marketplace.domain.payment.Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));

        // Authorization: only payment owner can view refund
        if (!refund.getPayment().getOrder().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Refund", "You can only view your own refunds");
        }

        return refund;
    }

    /**
     * Get all refunds for a payment.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.hien.marketplace.domain.payment.Refund> getRefundsForPayment(Long userId, Long paymentId) {
        // Verify user owns the payment
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (!payment.getOrder().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Refund", "You can only view refunds for your own payments");
        }

        return refundRepository.findByPaymentId(paymentId);
    }
}
