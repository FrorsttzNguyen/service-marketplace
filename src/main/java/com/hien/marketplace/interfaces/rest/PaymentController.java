package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.PaymentService;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter.UserPrincipal;
import com.hien.marketplace.interfaces.payment.dto.PaymentCreateRequest;
import com.hien.marketplace.interfaces.payment.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment operations.
 *
 * WHY: Exposes payment endpoints for authenticated users.
 * - POST /api/payments - Create payment for a booking
 * - GET /api/payments/{id} - Get payment details
 * - GET /api/payments/booking/{bookingId} - Get payment by booking
 *
 * All endpoints require JWT authentication (except webhook endpoint).
 * Authorization: Only booking owner can create/view payment.
 *
 * FLOW for creating payment:
 * 1. Customer calls POST /api/payments with bookingId
 * 2. Server creates Stripe PaymentIntent
 * 3. Server returns clientSecret
 * 4. Customer uses Stripe.js to confirm payment with clientSecret
 * 5. Stripe sends webhook to /api/webhooks/stripe
 * 6. Server updates payment status
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment creation and management")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Create a payment for a booking.
     *
     * Returns clientSecret for Stripe.js to confirm payment.
     *
     * FLOW:
     * 1. Validate booking ownership and status (must be CONFIRMED)
     * 2. Create Stripe PaymentIntent
     * 3. Save Payment with PaymentIntent ID
     * 4. Return clientSecret
     *
     * Frontend usage:
     * ```javascript
     * const response = await fetch('/api/payments', {
     *   method: 'POST',
     *   body: JSON.stringify({ bookingId: 123, paymentMethod: 'card' })
     * });
     * const { clientSecret } = await response.json();
     * // Use clientSecret with Stripe.js
     * stripe.confirmCardPayment(clientSecret, {...});
     * ```
     */
    @PostMapping
    @Operation(
            summary = "Create payment",
            description = "Create a payment for a confirmed booking. Returns clientSecret for Stripe.js.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Payment created, clientSecret returned"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "409", description = "Payment already exists for this booking"),
                    @ApiResponse(responseCode = "422", description = "Booking not eligible for payment")
            }
    )
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        String clientSecret = paymentService.createPayment(
                principal.userId(),
                request.bookingId(),
                request.paymentMethod()
        );

        // Get the created payment to build response
        Payment payment = paymentService.getPaymentByBookingId(principal.userId(), request.bookingId())
                .orElseThrow(() -> new IllegalStateException("Payment not found after creation"));

        PaymentResponse response = PaymentResponse.withClientSecret(payment, clientSecret);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get payment details by ID.
     *
     * Authorization: Only order owner can view payment.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get payment by ID",
            description = "Retrieve payment details. Only order owner can access.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment found"),
                    @ApiResponse(responseCode = "404", description = "Payment not found"),
                    @ApiResponse(responseCode = "403", description = "Not authorized to view this payment")
            }
    )
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Payment ID") @PathVariable Long id
    ) {
        Payment payment = paymentService.getPayment(principal.userId(), id);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    /**
     * Get payment by booking ID.
     *
     * Returns payment for a specific booking, if exists.
     * Authorization: Only booking owner can view payment.
     */
    @GetMapping("/booking/{bookingId}")
    @Operation(
            summary = "Get payment by booking ID",
            description = "Retrieve payment for a specific booking. Only booking owner can access.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment found"),
                    @ApiResponse(responseCode = "404", description = "No payment for this booking"),
                    @ApiResponse(responseCode = "403", description = "Not authorized to view this payment")
            }
    )
    public ResponseEntity<PaymentResponse> getPaymentByBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Booking ID") @PathVariable Long bookingId
    ) {
        return paymentService.getPaymentByBookingId(principal.userId(), bookingId)
                .map(payment -> ResponseEntity.ok(PaymentResponse.from(payment)))
                .orElse(ResponseEntity.notFound().build());
    }
}
