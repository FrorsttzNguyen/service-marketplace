package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Booking entity.
 *
 * WHY: Returns booking details with computed total price.
 * Customer and vendor both see this after booking creation.
 *
 * Note: totalPrice is computed from service.basePrice × quantity
 * (based on PricingType in service layer)
 */
public record BookingResponse(
    Long id,
    Long customerId,
    String customerName,
    Long serviceId,
    String serviceTitle,
    Long vendorId,
    String vendorName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    BookingStatus status,
    Integer quantity,
    BigDecimal totalPrice,
    String currency,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}