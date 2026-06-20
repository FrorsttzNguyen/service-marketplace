package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Service entity.
 *
 * WHY: Returns service details with provider info for public catalog.
 * Customers see this when browsing services.
 *
 * Note: Does NOT include internal fields like updatedAt or audit info.
 */
public record ServiceResponse(
    Long id,
    Long providerId,
    String providerName,
    Long categoryId,
    String categoryName,
    String title,
    String description,
    PricingType pricingType,
    BigDecimal basePrice,
    Integer durationHours,
    String address,
    String city,
    String imageUrl,
    ServiceStatus status,
    BigDecimal averageRating,
    Integer totalReviews,
    Integer totalBookings,
    LocalDateTime createdAt
) {
}