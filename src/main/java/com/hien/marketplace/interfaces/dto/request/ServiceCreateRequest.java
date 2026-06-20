package com.hien.marketplace.interfaces.dto.request;

import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new service.
 *
 * WHY: Provider creates services to offer to customers.
 * This DTO captures all required fields with validation.
 *
 * Note: providerId comes from authenticated user context, not from request body.
 * This prevents providers from creating services for other providers.
 */
public record ServiceCreateRequest(
    @NotNull(message = "Category ID is required")
    Long categoryId,

    @NotBlank(message = "Service title is required")
    @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    String title,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    @NotNull(message = "Pricing type is required")
    PricingType pricingType,

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    BigDecimal basePrice,

    // For HOURLY pricing: how many hours is one "unit"
    // For FIXED pricing: not applicable
    @Positive(message = "Duration hours must be positive")
    Integer durationHours,

    @Size(max = 500, message = "Address must not exceed 500 characters")
    String address,  // Service location (optional - some services are remote)

    @Size(max = 50, message = "City must not exceed 50 characters")
    String city,

    String imageUrl  // Optional: service cover image
) {
}