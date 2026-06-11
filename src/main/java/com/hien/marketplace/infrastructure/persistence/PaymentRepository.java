package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);
}
