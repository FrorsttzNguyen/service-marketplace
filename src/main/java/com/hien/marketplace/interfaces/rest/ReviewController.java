package com.hien.marketplace.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for review operations.
 *
 * WHY: Customers leave reviews after completed bookings.
 * Reviews affect vendor and service ratings.
 *
 * Note: Full implementation in Phase 3.
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Review management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    /**
     * Create review for completed booking.
     */
    @PostMapping
    @Operation(
            summary = "Create review",
            description = "Leave a review for a completed booking (Phase 3)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Review created"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Booking not completed")
            }
    )
    public ResponseEntity<Void> createReview() {
        // TODO: Phase 3 - Implement
        return ResponseEntity.status(501).build();
    }

    /**
     * Get reviews for service.
     */
    @GetMapping("/service/{serviceId}")
    @Operation(
            summary = "Get service reviews",
            description = "List all reviews for a service (Phase 3)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reviews retrieved")
            }
    )
    public ResponseEntity<Void> getServiceReviews(@PathVariable Long serviceId) {
        // TODO: Phase 3 - Implement
        return ResponseEntity.status(501).build();
    }

    /**
     * Get reviews for vendor.
     */
    @GetMapping("/vendor/{vendorId}")
    @Operation(
            summary = "Get vendor reviews",
            description = "List all reviews for a vendor (Phase 3)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reviews retrieved")
            }
    )
    public ResponseEntity<Void> getVendorReviews(@PathVariable Long vendorId) {
        // TODO: Phase 3 - Implement
        return ResponseEntity.status(501).build();
    }
}