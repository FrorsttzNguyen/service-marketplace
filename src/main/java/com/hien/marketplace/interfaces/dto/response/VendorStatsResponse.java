package com.hien.marketplace.interfaces.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for vendor dashboard statistics.
 *
 * WHY: Vendors need to understand their business performance.
 * Used for dashboard overview cards.
 *
 * Note: bookingsByStatus is a map of status -> count.
 * This allows frontend to render status breakdown.
 */
public record VendorStatsResponse(
    Integer totalServices,
    Integer activeServices,
    Integer totalBookings,
    Integer pendingBookings,
    Integer confirmedBookings,
    Integer completedBookings,
    Integer cancelledBookings,
    BigDecimal averageRating,
    Integer totalReviews,
    Map<String, Integer> bookingsByStatus,  // "PENDING" -> 5, "CONFIRMED" -> 10, etc.
    Integer totalCustomers  // Unique customers who booked this vendor
) {
}