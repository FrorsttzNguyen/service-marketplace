package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.*;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.domain.order.OrderStatus;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import com.hien.marketplace.domain.payment.events.PaymentFailedEvent;
import com.hien.marketplace.domain.payment.events.PaymentSucceededEvent;
import com.hien.marketplace.infrastructure.persistence.OrderRepository;
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
    private final OrderRepository orderRepository;
    private final StripeClient stripeClient;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentTransactionService paymentTransactionService;

    /**
     * Create a payment for an order.
     *
     * FLOW:
     * 1. Validate order ownership and status
     * 2. Check payment doesn't already exist
     * 3. Create Stripe PaymentIntent (OUTSIDE transaction)
     * 4. Create local Payment with PaymentIntent ID (INSIDE transaction)
     * 5. Update order status to PENDING_PAYMENT
     * 6. Return client secret for Stripe.js
     *
     * @param userId Current user ID (for authorization)
     * @param orderId Order to pay for
     * @param paymentMethod Payment method (e.g., "card")
     * @return PaymentIntent client secret for frontend
     */
    public String createPayment(Long userId, Long orderId, String paymentMethod) {
        log.info("Creating payment for order {} by user {}", orderId, userId);

        // Step 1: Validate order (read-only, no transaction needed yet)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // Authorization: only order owner can pay
        if (!order.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "You can only pay for your own orders");
        }

        // Business rule: order must be in CREATED status
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleViolationException(
                    "Order status",
                    "Order is not eligible for payment. Current status: " + order.getStatus()
            );
        }

        // Check if payment already exists for this order
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new DuplicateResourceException("Payment", "orderId", orderId);
        }

        // Step 2: Create Stripe PaymentIntent (OUTSIDE transaction)
        // CRITICAL: This is a network call, should not be in @Transactional
        PaymentIntent paymentIntent;
        try {
            paymentIntent = stripeClient.createPaymentIntent(order.getTotal(), orderId);
            log.info("Created Stripe PaymentIntent: {}", paymentIntent.getId());
        } catch (StripeException e) {
            log.error("Failed to create PaymentIntent for order {}: {}", orderId, e.getMessage());
            throw new StripeApiException("Failed to create payment intent: " + e.getMessage(), e);
        }

        // Step 3: Create local Payment and update order (INSIDE transaction)
        // Uses PaymentTransactionService for proper transaction boundary via Spring proxy
        Payment payment = paymentTransactionService.createPaymentWithOrderUpdate(
                order, paymentIntent.getId(), paymentMethod);

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
     * 3. Update Order status to PAID
     * 4. Publish PaymentSucceededEvent for listeners
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param failureReason Failure message (if failed)
     * @param failureCode Failure code (if failed)
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

        // Update order status
        Order order = payment.getOrder();
        order.markAsPaid();
        orderRepository.save(order);

        log.info("Payment {} succeeded, Order {} marked as PAID",
                payment.getId(), order.getId());

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

        // Authorization: only order owner can view payment
        if (!payment.getOrder().getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "You can only view your own payments");
        }

        return payment;
    }

    /**
     * Get payment by order ID.
     *
     * Authorization: Only order owner can view payment.
     *
     * @param userId Current user ID (for authorization)
     * @param orderId Order ID
     * @return Payment if found and user owns the order
     * @throws BusinessRuleViolationException if user doesn't own the order
     */
    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByOrderId(Long userId, Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> {
                    // Authorization: only order owner can view payment
                    if (!payment.getOrder().getCustomer().getId().equals(userId)) {
                        throw new BusinessRuleViolationException(
                                "Payment",
                                "You can only view payments for your own orders"
                        );
                    }
                    return payment;
                });
    }
}
