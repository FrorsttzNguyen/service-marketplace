package com.hien.marketplace.application.service;

import com.hien.marketplace.application.dto.RefundContext;
import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.domain.payment.RefundStatus;
import com.hien.marketplace.domain.payment.events.RefundProcessedEvent;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
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
 * - RefundTransactionService: DB mutations and reads (transactional)
 *
 * CONCURRENT SAFETY:
 * - Uses pessimistic locking (SELECT FOR UPDATE) on Payment
 * - Re-validates refund amount after lock to prevent over-refund race
 * - Explicit Order save (NOT cascade from Payment)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundTransactionService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Load refund context with all needed data transactionally.
     *
     * WHY IN THIS SERVICE (not RefundService)?
     * - Spring @Transactional uses proxy-based AOP
     * - Self-invocation (this.method()) bypasses the proxy
     * - If RefundService calls its own @Transactional method, it's ignored
     * - By moving here, RefundService calls another bean → proxy intercepts → transaction works
     *
     * @param paymentId Payment ID
     * @param userId User ID for authorization
     * @return RefundContext snapshot with all needed data
     */
    @Transactional(readOnly = true)
    public RefundContext loadRefundContext(Long paymentId, Long userId) {
        Payment payment = paymentRepository.findByIdWithBookingAndRefunds(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return new RefundContext(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getBooking().getId(),
                payment.getBooking().getStatus(),
                payment.getBooking().getCustomer().getId(),
                calculateAlreadyRefunded(payment)
        );
    }

    /**
     * Create Refund and update Order if full refund, atomically.
     *
     * TRANSACTION BOUNDARY:
     * - Payment is locked with pessimistic write lock
     * - Refund INSERT and Order UPDATE are in same transaction
     * - If either fails, both rollback
     * - Stripe Refund was created before this (outside transaction)
     *
     * CONCURRENT SAFETY:
     * - Pessimistic lock on Payment prevents concurrent refund modifications
     * - Re-validates remaining refundable amount AFTER lock (race condition protection)
     *
     * IMPORTANT: Payment-to-Booking has NO cascade.
     * We must explicitly save Booking after calling booking.refund().
     *
     * @param paymentId Payment ID (not detached Payment object)
     * @param refundAmount Amount to refund
     * @param reason Refund reason
     * @param stripeRefundId Stripe Refund ID
     * @return Saved Refund entity
     */
    @Transactional
    public Refund createRefundWithBookingUpdate(Long paymentId, Money refundAmount,
                                                 String reason, String stripeRefundId) {
        log.debug("Creating Refund entity for Payment {}", paymentId);

        // Lock payment to prevent concurrent refund modifications
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        // RACE CONDITION PROTECTION:
        // Re-validate remaining amount AFTER acquiring lock
        // Pre-validation in RefundService happened before Stripe call (can race)
        // This check inside transaction with lock is the final authority
        Money alreadyRefunded = calculateAlreadyRefunded(payment);
        Money remaining = payment.getAmount().subtract(alreadyRefunded);

        if (refundAmount.getAmountCents() > remaining.getAmountCents()) {
            log.warn("Refund amount {} exceeds remaining refundable {} - race condition detected",
                    refundAmount, remaining);
            throw new BusinessRuleViolationException(
                    "Refund amount",
                    String.format("Refund amount (%.2f) exceeds remaining refundable (%.2f)",
                            toDecimal(refundAmount), toDecimal(remaining))
            );
        }

        // Create Refund entity
        Refund refund = new Refund(payment, refundAmount, reason);
        refund.setStripeRefundId(stripeRefundId);
        refund.markAsSucceeded();

        refund = refundRepository.save(refund);

        // Update booking status if full refund
        // IMPORTANT: Check cumulative total, not just this refund amount
        // Two partial refunds summing to full should also trigger booking.refund()
        Money totalAfterRefund = alreadyRefunded.add(refundAmount);
        boolean isFullRefund = totalAfterRefund.equals(payment.getAmount());
        if (isFullRefund) {
            Booking booking = payment.getBooking();
            // changedBy null: refund is system-initiated (Stripe refund already processed).
            booking.refund(null, "Full refund");
            bookingRepository.save(booking); // EXPLICIT save - Payment.booking has NO cascade
            log.info("Booking {} marked as REFUNDED (full refund - total: {})", booking.getId(), totalAfterRefund);
        }

        // Publish domain event (within transaction)
        eventPublisher.publishEvent(RefundProcessedEvent.from(refund));

        log.debug("Refund {} created for Payment {}", refund.getId(), payment.getId());

        return refund;
    }

    /**
     * Calculate total amount already refunded for a payment.
     * Must be called within transaction where payment.refunds is loaded.
     */
    private Money calculateAlreadyRefunded(Payment payment) {
        return payment.getRefunds().stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
                .map(Refund::getAmount)
                .reduce(Money.of(0), Money::add);
    }

    /**
     * Convert Money to BigDecimal for display.
     */
    private java.math.BigDecimal toDecimal(Money money) {
        return java.math.BigDecimal.valueOf(money.getAmountCents(), 2);
    }
}