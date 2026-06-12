package com.hien.marketplace.application.service;

import com.hien.marketplace.application.dto.RefundContext;
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
 *
 * LAZY LOADING SAFE PATTERN:
 * - RefundContext snapshot loaded transactionally (loadRefundContext)
 * - Snapshot used for validation and Stripe call (no lazy loading outside tx)
 * - Final write via RefundTransactionService with locking
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
     * Load refund context with all needed data.
     *
     * WHY SEPARATE METHOD?
     * - RefundService.createRefund is NOT @Transactional (Stripe call outside)
     * - Accessing lazy relationships outside transaction throws LazyInitializationException
     * - This method loads all needed data in ONE transaction with JOIN FETCH
     *
     * @param paymentId Payment ID
     * @param userId User ID for authorization
     * @return RefundContext snapshot
     */
    @Transactional(readOnly = true)
    public RefundContext loadRefundContext(Long paymentId, Long userId) {
        Payment payment = paymentRepository.findByIdWithOrderAndRefunds(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return new RefundContext(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getAmount(),
                payment.getOrder().getId(),
                payment.getOrder().getStatus(),
                payment.getOrder().getCustomer().getId(),
                calculateTotalRefunded(payment)
        );
    }

    /**
     * Create a refund for a payment.
     *
     * FLOW:
     * 1. Load context (transactional read with all needed data)
     * 2. Validate ownership and status from snapshot
     * 3. Validate refund amount from snapshot
     * 4. Create Stripe Refund (OUTSIDE transaction)
     * 5. Create local Refund entity (INSIDE transaction)
     * 6. Update Order status if full refund
     * 7. Publish RefundProcessedEvent
     *
     * IMPORTANT: Uses RefundContext snapshot to avoid LazyInitializationException.
     * All lazy relationships are loaded in loadRefundContext(), which runs in a
     * separate @Transactional(readOnly = true) method.
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

        // Step 1: Load context transactionally (all lazy data loaded here)
        RefundContext context = loadRefundContext(paymentId, userId);

        // Step 2: Validate ownership from snapshot (no lazy loading)
        if (!context.isOwnedBy(userId)) {
            throw new BusinessRuleViolationException("Refund", "You can only refund your own payments");
        }

        // Business rule: only PAID orders can be refunded
        if (!context.isRefundable()) {
            throw new PaymentException(
                    "Order status",
                    "Only paid orders can be refunded. Current status: " + context.orderStatus(),
                    null
            );
        }

        // Determine refund amount
        Money refundAmount = amountCents != null
                ? Money.of(amountCents)
                : context.paymentAmount(); // Full refund

        // Step 3: Validate refund amount from snapshot (no lazy loading)
        validateRefundAmount(context, refundAmount);

        // Step 4: Create Stripe Refund (OUTSIDE transaction)
        com.stripe.model.Refund stripeRefund;
        try {
            stripeRefund = stripeClient.createRefund(
                    context.stripePaymentIntentId(),
                    refundAmount.equals(context.paymentAmount()) ? null : refundAmount.getAmountCents()
            );
            log.info("Created Stripe Refund: {}", stripeRefund.getId());
        } catch (StripeException e) {
            log.error("Failed to create refund for payment {}: {}", paymentId, e.getMessage());
            throw new StripeApiException("Failed to create refund: " + e.getMessage(), e);
        }

        // Step 5: Create local Refund (INSIDE transaction with locking)
        // Uses RefundTransactionService for proper transaction boundary via Spring proxy
        com.hien.marketplace.domain.payment.Refund refund = refundTransactionService.createRefundWithOrderUpdate(
                context.paymentId(), refundAmount, reason, stripeRefund.getId());

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
     *
     * @param context RefundContext snapshot with pre-loaded refund totals
     * @param refundAmount Amount to refund
     */
    private void validateRefundAmount(RefundContext context, Money refundAmount) {
        // Refund amount must be positive
        if (refundAmount.getAmountCents() <= 0) {
            throw new PaymentException("Refund amount", "Refund amount must be positive");
        }

        // Total refunds cannot exceed payment
        Money totalAfterRefund = context.alreadyRefunded().add(refundAmount);
        if (totalAfterRefund.getAmountCents() > context.paymentAmount().getAmountCents()) {
            throw new PaymentException(
                    "Refund amount",
                    String.format("Total refunds (%.2f) cannot exceed payment amount (%.2f)",
                            toDecimal(totalAfterRefund), toDecimal(context.paymentAmount()))
            );
        }
    }

    /**
     * Calculate total amount already refunded for a payment.
     *
     * IMPORTANT: This must be called within a transaction where
     * payment.getRefunds() is already loaded (via JOIN FETCH).
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
