package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.ReviewService;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.interfaces.dto.request.ReviewCreateRequest;
import com.hien.marketplace.interfaces.dto.response.ReviewResponse;
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

import java.util.List;

/**
 * REST controller for review operations.
 *
 * WHY: customers leave reviews after their bookings are COMPLETED; reviews drive the
 * denormalized service + provider ratings shown in the catalog.
 *
 * Authorization (see SecurityConfig):
 * - POST /api/reviews — authenticated (the customer who owns the booking)
 * - GET /api/reviews/service/** and /api/reviews/provider/** — public (browsable on detail pages)
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Review management endpoints")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Create a review for a completed booking.
     */
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create review",
            description = "Leave a review for a booking the caller owns. The booking must be COMPLETED " +
                    "and not already reviewed.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Review created"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Not your booking, not completed, or already reviewed")
            }
    )
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        ReviewResponse review = reviewService.createReview(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    /**
     * List reviews for a service (public).
     */
    @GetMapping("/service/{serviceId}")
    @Operation(
            summary = "Get service reviews",
            description = "List all reviews for a service, newest first.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reviews retrieved")
            }
    )
    public ResponseEntity<List<ReviewResponse>> getServiceReviews(@PathVariable Long serviceId) {
        return ResponseEntity.ok(reviewService.getServiceReviews(serviceId));
    }

    /**
     * List reviews for a provider (public).
     */
    @GetMapping("/provider/{providerId}")
    @Operation(
            summary = "Get provider reviews",
            description = "List all reviews for a provider, newest first.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reviews retrieved")
            }
    )
    public ResponseEntity<List<ReviewResponse>> getProviderReviews(@PathVariable Long providerId) {
        return ResponseEntity.ok(reviewService.getProviderReviews(providerId));
    }
}
