package com.hien.marketplace.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for Review entity.
 *
 * WHY: Returns review details with customer info for transparency.
 */
public record ReviewResponse(
    Long id,
    Long bookingId,
    Long serviceId,
    String serviceTitle,
    Long customerId,
    String customerName,
    Long providerId,
    String providerName,
    Integer rating,
    String comment,
    LocalDateTime createdAt
) {
}