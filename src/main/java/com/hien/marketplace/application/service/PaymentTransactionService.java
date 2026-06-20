package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
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
 * 1. Payment INSERT happens in a proper transaction
 * 2. Stripe API calls stay outside transaction (in PaymentService)
 *
 * ARCHITECTURE:
 * - PaymentService: orchestrates flow, Stripe API calls (non-transactional)
 * - PaymentTransactionService: DB mutations only (transactional)
 *
 * CONCURRENT SAFETY:
 * - Uses pessimistic locking (SELECT FOR UPDATE) on Booking
 * - DB unique constraint on payments.booking_id catches race conditions
 *
 * NOTE (Order→Booking merge): a payment is created against a CONFIRMED booking. The booking
 * status does NOT change here — it stays CONFIRMED while the Stripe charge is in flight, which
 * the Payment's own status (PENDING/PROCESSING) tracks. The booking only moves to PAID later,
 * when the success webhook arrives (PaymentService.handlePaymentSucceeded).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    /**
     * Create the local Payment for a booking, atomically and with locking.
     *
     * TRANSACTION BOUNDARY:
     * - Booking is locked with a pessimistic write lock
     * - Payment INSERT runs in this transaction
     * - Stripe PaymentIntent was created before this (outside transaction)
     *
     * CONCURRENT SAFETY:
     * - Pessimistic lock prevents concurrent booking modifications
     * - DB unique constraint on booking_id catches duplicate payments
     *
     * @param userId User ID for authorization check
     * @param bookingId Booking ID (not a detached Booking object)
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param paymentMethod Payment method (e.g., "card")
     * @return Saved Payment entity
     */
    @Transactional
    public Payment createPaymentWithBookingUpdate(Long userId, Long bookingId, String paymentIntentId, String paymentMethod) {
        log.debug("Creating Payment entity for Booking {}", bookingId);

        // Lock booking to prevent concurrent modifications
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // Authorization check inside transaction
        if (!booking.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException("Payment", "Not your booking");
        }

        // Business rule check: booking must be CONFIRMED to be payable
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException(
                    "Booking status",
                    "Booking is not eligible for payment. Current status: " + booking.getStatus()
            );
        }

        // Duplicate payment check inside transaction (after lock acquired)
        if (paymentRepository.existsByBookingId(bookingId)) {
            throw new BusinessRuleViolationException("Payment", "Payment already exists for this booking");
        }

        // Create Payment entity — charge the booking's TOTAL (subtotal + commission)
        Payment payment = new Payment(booking, booking.getTotal());
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setPaymentMethod(paymentMethod);
        payment.markAsProcessing();

        payment = paymentRepository.save(payment);

        log.debug("Payment {} created (PROCESSING) for Booking {} — booking stays CONFIRMED until webhook",
                payment.getId(), booking.getId());

        return payment;
    }
}
