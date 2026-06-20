package com.hien.marketplace.interfaces.dto.request;

import com.hien.marketplace.domain.service.PricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for updating an existing service.
 *
 * WHY: All fields are optional (null = don't update).
 * Provider can update only what they want to change.
 *
 * Note: id comes from URL path, not request body.
 * providerId is validated against authenticated user to ensure ownership.
 */
public record ServiceUpdateRequest(
    @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    String title,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    PricingType pricingType,

    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    BigDecimal basePrice,

    @Positive(message = "Duration hours must be positive")
    Integer durationHours,

    @Size(max = 500, message = "Address must not exceed 500 characters")
    String address,

    @Size(max = 50, message = "City must not exceed 50 characters")
    String city,

    String imageUrl,

    Boolean active  // Provider can activate/deactivate service
) {
}