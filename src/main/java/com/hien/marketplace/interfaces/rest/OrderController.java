package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.OrderService;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.interfaces.dto.request.CreateOrderRequest;
import com.hien.marketplace.interfaces.dto.response.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
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
 * REST controller for order operations.
 *
 * WHY: Order links Booking to Payment. Created after a booking is CONFIRMED; PaymentService then
 * needs an Order in the CREATED status to issue a Stripe PaymentIntent (the book → pay flow).
 *
 * Idempotency: POST /api/orders is safe to retry. OrderService#createFromBooking returns the
 * existing order if one already exists for the booking (and it's still payable), so a double submit
 * never creates a duplicate row. See OrderService for the exact PAYABLE_STATUSES rule.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    /**
     * Create an order from a confirmed booking.
     *
     * STATUS CODE POLICY (deliberate, documented):
     * We return 201 CREATED for BOTH a newly created order AND the idempotent "already existed,
     * still payable" case. The alternative — 200 for the existed case — would require the service
     * to distinguish "created" vs "reused", which adds a second DB round-trip or a leaky return
     * type for no real client benefit: either way the client gets the same payable OrderResponse
     * and proceeds to payment. 201 keeps the contract uniform ("you have a payable order now").
     * The OrderService guarantees only ONE order row ever exists per booking.
     */
    @PostMapping
    @Operation(
            summary = "Create order",
            description = "Create an order from a confirmed booking. Idempotent on bookingId: " +
                    "if a payable order already exists for the booking, it is returned instead of " +
                    "duplicated. An order is a prerequisite for creating a payment.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order created (or existing payable order returned)"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Booking not confirmed, an order already exists and is no longer payable, or caller is not the booking's customer")
            }
    )
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse order = orderService.createFromBooking(principal.userId(), request.bookingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get order",
            description = "Get order details. Only the order's customer may view it.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found"),
                    @ApiResponse(responseCode = "404", description = "Order not found"),
                    @ApiResponse(responseCode = "422", description = "Caller is not the order's customer")
            }
    )
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        OrderResponse order = orderService.getOrder(principal.userId(), id);
        return ResponseEntity.ok(order);
    }
}
