package com.hien.marketplace.application.service;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.domain.payment.events.RefundProcessedEvent;
import com.hien.marketplace.infrastructure.persistence.PaymentRepository;
import com.hien.marketplace.infrastructure.persistence.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional service for Refund database operations.
 *
 * WHY SEPARATE SERVICE?
 * Spring's @Transactional uses proxy-based AOP.
 * Self-invocation (calling @Transactional method from same class)
 * does NOT apply transaction semantics.
 *
 * By moving DB mutations to this separate bean, we ensure:
 * 1. Refund and Order updates are atomic
 * 2. Transaction is properly applied via Spring proxy
 * 3. Stripe API calls stay outside transaction (in RefundService)
 *
 * ARCHITECTURE:
 * - RefundService: orchestrates flow, Stripe API calls (non-transactional)
 * - RefundTransactionService: DB mutations only (transactional)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundTransactionService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create Refund and update Order if full refund, atomically.
     *
     * TRANSACTION BOUNDARY:
     * - Refund INSERT, Order UPDATE, and event publish are in same transaction
     * - If any fails, all rollback
     * - Stripe Refund was created before this (outside transaction)
     *
     * @param payment Payment being refunded
     * @param refundAmount Amount to refund
     * @param reason Refund reason
     * @param stripeRefundId Stripe Refund ID
     * @return Saved Refund entity
     */
    @Transactional
    public Refund createRefundWithOrderUpdate(Payment payment, Money refundAmount,
                                               String reason, String stripeRefundId) {
        log.debug("Creating Refund entity for Payment {}", payment.getId());

        // Create Refund entity
        Refund refund = new Refund(payment, refundAmount, reason);
        refund.setStripeRefundId(stripeRefundId);
        refund.markAsSucceeded();

        refund = refundRepository.save(refund);

        // Update order status if full refund
        boolean isFullRefund = refundAmount.equals(payment.getAmount());
        if (isFullRefund) {
            payment.getOrder().refund();
            paymentRepository.save(payment); // Cascades to order
            log.info("Order {} marked as REFUNDED (full refund)", payment.getOrder().getId());
        }

        // Publish domain event (within transaction)
        eventPublisher.publishEvent(RefundProcessedEvent.from(refund));

        log.debug("Refund {} created for Payment {}", refund.getId(), payment.getId());

        return refund;
    }
}