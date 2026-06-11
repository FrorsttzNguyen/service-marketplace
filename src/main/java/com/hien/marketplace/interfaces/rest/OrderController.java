package com.hien.marketplace.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for order operations.
 *
 * WHY: Order links Booking to Payment.
 * Created after booking is confirmed.
 *
 * Note: Full implementation in Phase 3-4 with payment integration.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    /**
     * Create order from booking.
     *
     * Note: Phase 4 will implement full payment flow.
     */
    @PostMapping
    @Operation(
            summary = "Create order",
            description = "Create an order from a confirmed booking (Phase 4)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order created"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Booking not confirmed")
            }
    )
    public ResponseEntity<Void> createOrder() {
        // TODO: Phase 4 - Implement with Stripe payment integration
        return ResponseEntity.status(501).build();  // Not Implemented
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get order",
            description = "Get order details (Phase 3)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    public ResponseEntity<Void> getOrder(@PathVariable Long id) {
        // TODO: Phase 3 - Implement
        return ResponseEntity.status(501).build();
    }
}