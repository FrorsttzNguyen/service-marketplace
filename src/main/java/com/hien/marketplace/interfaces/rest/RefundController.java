package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.RefundService;
import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter.UserPrincipal;
import com.hien.marketplace.interfaces.payment.dto.RefundRequest;
import com.hien.marketplace.interfaces.payment.dto.RefundResponse;
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

import java.util.List;

/**
 * REST controller for refund operations.
 *
 * WHY: Exposes refund endpoints for authenticated users.
 * - POST /api/refunds - Create refund for a payment
 * - GET /api/refunds/{id} - Get refund details
 * - GET /api/refunds/payment/{paymentId} - Get all refunds for a payment
 *
 * REFUND TYPES:
 * - Full refund: Omit amountCents in request body
 * - Partial refund: Provide amountCents < payment amount
 *
 * AUTHORIZATION: Only payment owner can request refund.
 */
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@Tag(name = "Refunds", description = "Refund creation and management")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {

    private final RefundService refundService;

    /**
     * Create a refund for a payment.
     *
     * FLOW:
     * 1. Validate payment ownership and status (must be SUCCEEDED)
     * 2. Validate refund amount (total refunds <= payment amount)
     * 3. Create Stripe Refund
     * 4. Save Refund entity
     * 5. Update Order status if full refund
     *
     * EXAMPLES:
     * - Full refund: POST /api/refunds with body {"paymentId": 123}
     * - Partial refund: POST /api/refunds with body {"paymentId": 123, "amountCents": 5000}
     */
    @PostMapping
    @Operation(
            summary = "Create refund",
            description = "Create a refund for a payment. Omit amountCents for full refund.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Refund created"),
                    @ApiResponse(responseCode = "404", description = "Payment not found"),
                    @ApiResponse(responseCode = "422", description = "Payment not refundable or invalid amount")
            }
    )
    public ResponseEntity<RefundResponse> createRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Payment ID") @RequestParam Long paymentId,
            @Valid @RequestBody(required = false) RefundRequest request
    ) {
        // Handle null request (full refund)
        RefundRequest refundRequest = request != null ? request : new RefundRequest(null, null);

        Refund refund = refundService.createRefund(
                principal.userId(),
                paymentId,
                refundRequest.amountCents(),
                refundRequest.reason()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(RefundResponse.from(refund));
    }

    /**
     * Get refund details by ID.
     *
     * Authorization: Only payment owner can view refund.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get refund by ID",
            description = "Retrieve refund details.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Refund found"),
                    @ApiResponse(responseCode = "404", description = "Refund not found")
            }
    )
    public ResponseEntity<RefundResponse> getRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Refund ID") @PathVariable Long id
    ) {
        Refund refund = refundService.getRefund(principal.userId(), id);
        return ResponseEntity.ok(RefundResponse.from(refund));
    }

    /**
     * Get all refunds for a payment.
     *
     * Returns list of all refunds (partial refunds can be multiple).
     */
    @GetMapping("/payment/{paymentId}")
    @Operation(
            summary = "Get refunds for payment",
            description = "Retrieve all refunds for a specific payment.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Refunds found"),
                    @ApiResponse(responseCode = "404", description = "Payment not found")
            }
    )
    public ResponseEntity<List<RefundResponse>> getRefundsForPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Payment ID") @PathVariable Long paymentId
    ) {
        List<Refund> refunds = refundService.getRefundsForPayment(principal.userId(), paymentId);
        List<RefundResponse> responses = refunds.stream()
                .map(RefundResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
