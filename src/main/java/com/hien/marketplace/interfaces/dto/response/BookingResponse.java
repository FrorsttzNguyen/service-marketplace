package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Booking entity.
 *
 * WHY: Returns booking details with the full money breakdown.
 * Customer and vendor both see this after booking creation.
 *
 * Money fields (after the Order→Booking merge the booking carries all three):
 * - totalPrice  = subtotal = service.basePrice × quantity (the service price shown in listings)
 * - commission  = platform fee on top of the subtotal
 * - total       = subtotal + commission = the amount the customer actually pays at checkout
 */
public record BookingResponse(
    Long id,
    Long customerId,
    String customerName,
    Long serviceId,
    String serviceTitle,
    Long vendorId,
    String vendorName,
    String serviceStreet,
    String serviceCity,
    String serviceZipCode,
    LocalDateTime startTime,
    LocalDateTime endTime,
    BookingStatus status,
    Integer quantity,
    BigDecimal totalPrice,
    BigDecimal commission,
    BigDecimal total,
    String currency,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
