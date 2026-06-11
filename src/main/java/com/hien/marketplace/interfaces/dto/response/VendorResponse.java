package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.vendor.VerificationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Vendor entity.
 *
 * WHY: Vendor extends User via composition (has-a User).
 * This DTO flattens the structure for API consumers.
 *
 * Note: Does NOT include sensitive data like bank account info.
 * That would require separate admin-only endpoint.
 */
public record VendorResponse(
    Long id,
    Long userId,
    String businessName,
    String description,
    String address,
    VerificationStatus verificationStatus,
    BigDecimal averageRating,
    Integer totalReviews,
    Integer completedBookings,
    LocalDateTime createdAt
) {
}