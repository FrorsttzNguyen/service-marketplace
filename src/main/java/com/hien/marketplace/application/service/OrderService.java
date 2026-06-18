package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.OrderMapper;
import com.hien.marketplace.config.CommissionProperties;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.domain.order.OrderStatus;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.OrderRepository;
import com.hien.marketplace.interfaces.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service for order operations — the glue between Booking and Payment.
 *
 * WHY this exists (Phase 4 order glue): PaymentService.createPayment requires an Order in the
 * CREATED status. An Order is built from a CONFIRMED Booking: its subtotal is the booking's
 * total price, and a platform commission is added on top. This service owns that creation +
 * the read path, with authorization + idempotency handled here (not in the controller).
 *
 * Authorization mirrors BookingService.cancelBooking: the caller must be the booking's customer.
 * Forbidden access surfaces as BusinessRuleViolationException → 422 (same convention used across
 * the codebase; see PaymentService.getPayment's ownership check).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /**
     * Order statuses for which the order is still "payable" — i.e. returning the existing order
     * from a repeat create call is the right thing (the customer can still proceed to payment).
     * Any status NOT in this set is terminal/already-paid and a repeat create is a conflict.
     */
    private static final Set<OrderStatus> PAYABLE_STATUSES = Set.of(
            OrderStatus.CREATED,
            OrderStatus.PENDING_PAYMENT
    );

    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CommissionProperties commissionProperties;

    /**
     * Create an order from a confirmed booking (idempotent on bookingId).
     *
     * Flow:
     * 1. Load the booking (404 if missing).
     * 2. Authorize: caller must be the booking's CUSTOMER (422 if not — mirrors cancelBooking).
     * 3. IDEMPOTENCY: if an order already exists for this booking, return it as-is when it's still
     *    payable; otherwise surface a conflict. This makes POST /api/orders safe to retry: a double
     *    submit (network flake, double-click) never creates a second order row for the same booking.
     * 4. Status gate: booking must be CONFIRMED. PENDING/IN_PROGRESS/etc → 422.
     * 5. Money: subtotal = booking.totalPrice (the same Money value object already on the booking).
     *    commission = subtotal × app.commission.rate, computed in CENTS (see commissionCents formula).
     *    Build the Order (constructor computes total = subtotal + commission, sets status CREATED),
     *    persist, return mapped.
     *
     * @param userId    the caller's user id (from JWT)
     * @param bookingId the booking to create an order from
     * @return the order response (newly created, or the existing payable order for this booking)
     */
    @Transactional
    public OrderResponse createFromBooking(Long userId, Long bookingId) {
        // Step 1: load booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // Step 2: authorization — only the customer who owns the booking may order from it
        if (!booking.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException(
                    "Order ownership",
                    "You can only create orders for your own bookings"
            );
        }

        // Step 3: idempotency — a booking maps to at most one order. If one exists, decide
        // whether to return it (still payable) or reject (already paid / terminal).
        var existing = orderRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            Order prior = existing.get();
            if (PAYABLE_STATUSES.contains(prior.getStatus())) {
                log.info("Returning existing payable order {} for booking {}", prior.getId(), bookingId);
                return orderMapper.toResponse(prior);
            }
            // Terminal/already-paid: a brand-new order can't be created, and reusing this one
            // would let the customer "pay again" for something already settled. Surface a conflict.
            throw new BusinessRuleViolationException(
                    "Order creation",
                    "An order for this booking already exists and is no longer payable (status: "
                            + prior.getStatus() + ")"
            );
        }

        // Step 4: status gate — an order requires a CONFIRMED booking. This is the book→pay seam.
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException(
                    "Booking status",
                    "Booking must be confirmed before ordering. Current status: " + booking.getStatus()
            );
        }

        // Step 5: money math — no floating point on money. Commission is derived in cents.
        Money subtotal = booking.getTotalPrice();
        Money commission = computeCommission(subtotal);

        Order order = new Order(booking.getCustomer(), booking, subtotal, commission);
        order = orderRepository.save(order);
        log.info("Created order {} for booking {} (subtotal={}, commission={})",
                order.getId(), bookingId, subtotal, commission);

        return orderMapper.toResponse(order);
    }

    /**
     * Read a single order, scoped to its owner.
     *
     * Authorization mirrors the rest of the codebase: a non-owner gets a BusinessRuleViolationException
     * (→ 422). We do NOT 404-and-hide here (unlike PaymentService.getPaymentByOrderId) because orders
     * aren't as sensitive a resource as payment intents and the existing order/payment read patterns
     * differ deliberately; we follow OrderController's existing "get order" convention.
     *
     * @param userId  the caller's user id (from JWT)
     * @param orderId the order id
     * @return the mapped order response
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.getCustomer().getId().equals(userId)) {
            throw new BusinessRuleViolationException(
                    "Order ownership",
                    "You can only view your own orders"
            );
        }

        return orderMapper.toResponse(order);
    }

    /**
     * Compute the platform commission as a Money value, entirely in integer cents.
     *
     * Formula:
     *   commissionCents = round_half_up( subtotalCents × rate )
     *
     * Why cents + HALF_UP:
     *   - Money is stored as integer cents (see Money). Keeping the whole calculation in cents
     *     means there is never a floating-point step (e.g. 0.10 * 1999 in double is 199.89999…,
     *     which would truncate wrong). BigDecimal on the two integer-derived inputs is exact.
     *   - HALF_UP rounds the half-cent the way a human expects (10.5 → 11), matching how the
     *     existing Money.multiply(BigDecimal) helper rounds. We reuse that helper so the rounding
     *     policy lives in ONE place (Money), not duplicated here.
     *
     * Example: subtotal = $19.99 (1999 cents), rate = 0.10 → 1999 × 0.10 = 199.9 → 200 cents commission.
     *
     * @param subtotal the booking's total price (Money)
     * @return commission as Money (cents)
     */
    private Money computeCommission(Money subtotal) {
        return subtotal.multiply(commissionProperties.getRate());
    }
}
