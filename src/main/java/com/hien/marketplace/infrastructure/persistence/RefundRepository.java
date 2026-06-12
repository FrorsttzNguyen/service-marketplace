package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.domain.payment.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for Refund entity.
 */
public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * Find all refunds for a payment.
     */
    List<Refund> findByPaymentId(Long paymentId);

    /**
     * Find refunds by status.
     */
    List<Refund> findByStatus(RefundStatus status);
}
