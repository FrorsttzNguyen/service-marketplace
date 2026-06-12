package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Check if payment exists for an order.
     * Used to prevent duplicate payment creation.
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Find payments by status.
     * Used for webhook processing and monitoring.
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Find payment by order ID.
     * Used to check payment status for an order.
     */
    Optional<Payment> findByOrderId(Long orderId);
}
