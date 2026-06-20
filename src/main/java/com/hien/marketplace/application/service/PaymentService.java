package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.*;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.events.PaymentFailedEvent;
import com.hien.marketplace.domain.payment.events.PaymentSucceededEvent;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.PaymentRepository;
import com.hien.marketplace.infrastructure.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for payment operations.
 *
 * ARCHITECTURE PATTERN:
 * - Stripe API calls OUTSIDE @Transactional (network I/O)
 * - Database operations INSIDE @Transactional (via PaymentTransactionService)
 *
 * WHY this pattern?
 * 1. Network I/O should never hold database transactions open
 *    - Stripe API call can take 100ms-2s
 *    - Database transaction would be held open for that duration
 *    - Connection pool exhaustion under load
 *
 * 2. Failure handling
 *    - If Stripe succeeds but DB rolls back = orphan PaymentIntent
 *    - If Stripe fails, DB doesn't commit = clean state
 *
 * 3. Transaction semantics
 *    - Self-invocation of @Transactional methods doesn't work (Spring proxy limitation)
 *    - DB mutations moved to PaymentTransactionService for proper transaction boundary
 *
 * FLOW for createPayment:
 * 1. Validate order (transactional read)
 * 2. Create Stripe PaymentIntent (OUTSIDE transaction)
 * 3. Save Payment with intent ID (INSIDE transaction via PaymentTransactionService)
 * 4. Return client secret for frontend
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final StripeClient stripeClient;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentTransactionService paymentTransactionService;

    /**
     * Create a payment for an order.
     *
     * FLOW:
     * 1. Quick pre-validation (ownership, status) - can race
     * 2. Create Stripe PaymentIntent (OUTSIDE transaction)
     * 3. Create local Payment with locking (INSIDE transaction)
     *    - Pessimistic lock on Order
     *    - DB unique constraint catches duplicates
     * 4. Return client secret for Stripe.js
     *
     * RACE CONDITION HANDLING:
     * - Pre-validation before Stripe is for UX (fast fail)
     * - Real safety is in transaction service (locking + unique constraint)
     *
     * @param userId Current user ID (for authorization)
     * @param bookingId Booking to pay for (must be CONFIRMED)
     * @param paymentMethod Payment method (e.g., "card")
     * @return PaymentIntent client secret for frontend
     */
    public String createPayment(Long userId, Long bookingId, String paymentMethod) {
        log.info("Creating payment for booking {} by user {}", bookingId, userId);

        // Step 1: Quick pre-validation (read-only, can race)
        // Real validation happens inside transaction with locking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // Pre-check authorization (fast fail for UX)
        if (!booking.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "You can only pay for your own bookings");
        }

        // Pre-check status (fast fail for UX) — booking must be CONFIRMED to be payable
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException(
                    "Booking status",
                    "Booking is not eligible for payment. Current status: " + booking.getStatus()
            );
        }

        // Pre-check duplicate (fast fail for UX)
        if (paymentRepository.existsByBookingId(bookingId)) {
            throw new DuplicateResourceException("Payment", "bookingId", bookingId);
        }

        // Step 2: Create Stripe PaymentIntent (OUTSIDE transaction)
        // CRITICAL: This is a network call, should not be in @Transactional
        PaymentIntent paymentIntent;
        try {
            paymentIntent = stripeClient.createPaymentIntent(booking.getTotal(), bookingId);
            log.info("Created Stripe PaymentIntent: {}", paymentIntent.getId());
        } catch (StripeException e) {
            log.error("Failed to create PaymentIntent for booking {}: {}", bookingId, e.getMessage());
            throw new StripeApiException("Failed to create payment intent: " + e.getMessage(), e);
        }

        // Step 3: Create local Payment (INSIDE transaction)
        // Uses PaymentTransactionService for proper transaction boundary
        // Pessimistic locking + DB unique constraint provide real safety
        Payment payment = paymentTransactionService.createPaymentWithBookingUpdate(
                userId, bookingId, paymentIntent.getId(), paymentMethod);

        log.info("Payment created successfully: paymentId={}, intentId={}",
                payment.getId(), paymentIntent.getId());

        // Return client secret for Stripe.js
        return paymentIntent.getClientSecret();
    }

    /**
     * Handle successful payment from webhook.
     *
     * Called when Stripe webhook receives payment_intent.succeeded.
     *
     * FLOW:
     * 1. Find Payment by stripePaymentIntentId
     * 2. Update status to SUCCEEDED (validates transition)
     * 3. Update Booking status to PAID
     * 4. Publish PaymentSucceededEvent for listeners
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     */
    @Transactional
    public void handlePaymentSucceeded(String paymentIntentId) {
        log.info("Handling payment success for PaymentIntent: {}", paymentIntentId);

        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> {
                    log.error("Payment not found for PaymentIntent: {}", paymentIntentId);
                    return new ResourceNotFoundException("Payment", "stripePaymentIntentId", paymentIntentId);
                });

        // Update payment status (validates state transition)
        payment.markAsSucceeded();

        // Update booking status: CONFIRMED → PAID. changedBy is null because this is a
        // system action triggered by the Stripe webhook, not a logged-in user.
        Booking booking = payment.getBooking();
        booking.markAsPaid(null);
        bookingRepository.save(booking);

        log.info("Payment {} succeeded, Booking {} marked as PAID",
                payment.getId(), booking.getId());

        // Publish domain event for async processing
        eventPublisher.publishEvent(PaymentSucceededEvent.from(payment));
    }

    /**
     * Handle failed payment from webhook.
     *
     * Called when Stripe webhook receives payment_intent.payment_failed.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param failureReason Human-readable error message
     * @param failureCode Machine-readable error code
     */
    @Transactional
    public void handlePaymentFailed(String paymentIntentId, String failureReason, String failureCode) {
        log.info("Handling payment failure for PaymentIntent: {}", paymentIntentId);

        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> {
                    log.error("Payment not found for PaymentIntent: {}", paymentIntentId);
                    return new ResourceNotFoundException("Payment", "stripePaymentIntentId", paymentIntentId);
                });

        // Update payment status (validates state transition)
        payment.markAsFailed();

        log.warn("Payment {} failed: {} (code: {})",
                payment.getId(), failureReason, failureCode);

        // Publish domain event for async processing
        eventPublisher.publishEvent(PaymentFailedEvent.from(payment, failureReason, failureCode));
    }

    /**
     * Get payment by ID.
     *
     * @param userId Current user ID (for authorization)
     * @param paymentId Payment ID
     * @return Payment entity
     */
    @Transactional(readOnly = true)
    public Payment getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // Authorization: only booking owner can view payment
        if (!payment.getBooking().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "You can only view your own payments");
        }

        return payment;
    }

    /**
     * Get payment by booking ID.
     *
     * AUTHORIZATION POLICY:
     * - Returns 404 if payment not found OR user doesn't own the booking
     * - This prevents leaking existence of other users' payments
     * - Both "not found" and "unauthorized" return empty Optional (identical response)
     *
     * WHY NOT THROW ON UNAUTHORIZED?
     * - Throwing BusinessRuleViolationException returns 422
     * - Returning empty Optional returns 404
     * - If we throw 422 for unauthorized but 404 for not found, attacker can
     *   determine if another user's booking has a payment (information leak)
     * - Both cases return empty Optional → 404 → no leak
     *
     * @param userId Current user ID (for authorization)
     * @param bookingId Booking ID
     * @return Payment if found and user owns the booking, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByBookingId(Long userId, Long bookingId) {
        // Query by both bookingId AND customerId - unauthorized returns empty
        // This is intentionally identical to "not found" (no information leak)
        return paymentRepository.findByBookingIdAndBookingCustomerId(bookingId, userId);
    }
}
