package com.hien.marketplace.application.service;

import com.hien.marketplace.domain.order.Order;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * Create Payment and update Order atomically.
     *
     * TRANSACTION BOUNDARY:
     * - Both Payment INSERT and Order UPDATE are in same transaction
     * - If either fails, both rollback
     * - Stripe PaymentIntent was created before this (outside transaction)
     *
     * @param order Order to pay for
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param paymentMethod Payment method (e.g., "card")
     * @return Saved Payment entity
     */
    @Transactional
    public Payment createPaymentWithOrderUpdate(Order order, String paymentIntentId, String paymentMethod) {
        log.debug("Creating Payment entity for Order {}", order.getId());

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