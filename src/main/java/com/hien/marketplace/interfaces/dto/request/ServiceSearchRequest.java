package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for searching/filtering services.
 *
 * WHY: All fields are optional — client can filter by any combination.
 * This DTO captures all possible search parameters in one place.
 *
 * Used by: ServiceController.search() with pagination
 */
public record ServiceSearchRequest(
    String keyword,  // Search in title and description

    Long categoryId,  // Filter by category

    Long vendorId,  // Filter by vendor

    String city,  // Filter by city

    @Min(value = 0, message = "Min price cannot be negative")
    BigDecimal minPrice,

    @Min(value = 0, message = "Max price cannot be negative")
    BigDecimal maxPrice,

    @Min(value = 1, message = "Min rating must be at least 1")
    @Max(value = 5, message = "Min rating must be at most 5")
    Integer minRating,

    String sortBy,  // "price", "rating", "createdAt", "popularity"
    String sortOrder  // "asc", "desc"

    // Pagination handled by Spring Data Pageable (page, size parameters)
) {
}