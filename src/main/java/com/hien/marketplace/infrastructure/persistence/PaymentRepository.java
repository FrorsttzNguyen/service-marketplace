package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Check if payment exists for a booking.
     * Used to prevent duplicate payment creation.
     */
    boolean existsByBookingId(Long bookingId);

    /**
     * Find payments by status.
     * Used for webhook processing and monitoring.
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Find payment by booking ID.
     * Used to check payment status for a booking.
     */
    Optional<Payment> findByBookingId(Long bookingId);

    /**
     * Find payment by booking ID and customer ID.
     * Used for secure payment lookup - both conditions must match.
     * Returns empty if payment doesn't exist OR user doesn't own it.
     * This prevents leaking existence of other users' payments.
     */
    @Query("select p from Payment p where p.booking.id = :bookingId and p.booking.customer.id = :customerId")
    Optional<Payment> findByBookingIdAndBookingCustomerId(Long bookingId, Long customerId);

    /**
     * Find payment by ID with booking and refunds eagerly loaded.
     * Used for refund processing to avoid LazyInitializationException.
     *
     * WHY NEEDED?
     * - RefundService.createRefund is NOT @Transactional (Stripe call outside transaction)
     * - Accessing lazy relationships outside transaction throws LazyInitializationException
     * - This query fetches all needed data in ONE query with JOIN FETCH
     *
     * @param id Payment ID
     * @return Payment with booking.customer and refunds loaded
     */
    @Query("select p from Payment p " +
           "join fetch p.booking b " +
           "join fetch b.customer " +
           "left join fetch p.refunds " +
           "where p.id = :id")
    Optional<Payment> findByIdWithBookingAndRefunds(Long id);

    /**
     * Find payment by ID with pessimistic write lock.
     * Used during refund creation to prevent concurrent refund modifications.
     * Lock is held until the transaction commits.
     *
     * @param paymentId Payment ID
     * @return Payment with lock, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(Long paymentId);
}
