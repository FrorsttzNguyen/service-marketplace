package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for creating a review.
 *
 * WHY: Customer reviews a completed booking.
 * Reviews affect provider rating and service visibility.
 *
 * Validation:
 * - Rating: 1-5 stars (inclusive)
 * - One review per booking (enforced in service layer)
 * - Booking must be COMPLETED status
 */
public record ReviewCreateRequest(
    @NotNull(message = "Booking ID is required")
    Long bookingId,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    Integer rating,

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    String comment
) {
}