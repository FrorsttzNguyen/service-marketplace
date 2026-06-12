package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.domain.order.OrderStatus;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.infrastructure.persistence.OrderRepository;
import com.hien.marketplace.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional service for Payment database operations.
 *
 * WHY SEPARATE SERVICE?
 * Spring's @Transactional uses proxy-based AOP.
 * Self-invocation (calling @Transactional method from same class)
 * does NOT apply transaction semantics.
 *
 * By moving DB mutations to this separate bean, we ensure:
 * 1. Payment and Order updates are atomic
 * 2. Transaction is properly applied via Spring proxy
 * 3. Stripe API calls stay outside transaction (in PaymentService)
 *
 * ARCHITECTURE:
 * - PaymentService: orchestrates flow, Stripe API calls (non-transactional)
 * - PaymentTransactionService: DB mutations only (transactional)
 *
 * CONCURRENT SAFETY:
 * - Uses pessimistic locking (SELECT FOR UPDATE) on Order
 * - DB unique constraint on payments.order_id catches race conditions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * Create Payment and update Order atomically with locking.
     *
     * TRANSACTION BOUNDARY:
     * - Order is locked with pessimistic write lock
     * - Payment INSERT and Order UPDATE are in same transaction
     * - If either fails, both rollback
     * - Stripe PaymentIntent was created before this (outside transaction)
     *
     * CONCURRENT SAFETY:
     * - Pessimistic lock prevents concurrent order modifications
     * - DB unique constraint on order_id catches duplicate payments
     *
     * @param userId User ID for authorization check
     * @param orderId Order ID (not detached Order object)
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param paymentMethod Payment method (e.g., "card")
     * @return Saved Payment entity
     */
    @Transactional
    public Payment createPaymentWithOrderUpdate(Long userId, Long orderId, String paymentIntentId, String paymentMethod) {
        log.debug("Creating Payment entity for Order {}", orderId);

        // Lock order to prevent concurrent modifications
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // Authorization check inside transaction
        if (!order.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "Not your order");
        }

        // Business rule check: order must be in CREATED status
        if (!order.getStatus().equals(OrderStatus.CREATED)) {
            throw new BusinessRuleViolationException(
                    "Order status",
                    "Order is not eligible for payment. Current status: " + order.getStatus()
            );
        }

        // Duplicate payment check inside transaction (after lock acquired)
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new BusinessRuleViolationException("Payment", "Payment already exists for this order");
        }

        // Create Payment entity
        Payment payment = new Payment(order, order.getTotal());
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setPaymentMethod(paymentMethod);
        payment.markAsProcessing();

        payment = paymentRepository.save(payment);

        // Update order status
        order.markAsPendingPayment();
        orderRepository.save(order);

        log.debug("Payment {} created, Order {} marked as PENDING_PAYMENT",
                payment.getId(), order.getId());

        return payment;
    }
}