package com.hien.marketplace.interfaces.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for vendor dashboard earnings.
 *
 * WHY: Vendors need to track their earnings over time.
 * Used for financial reporting and payout tracking.
 *
 * Note: earningsByMonth is a map of "YYYY-MM" -> total earnings.
 * This allows frontend to render charts.
 */
public record VendorEarningsResponse(
    BigDecimal totalEarnings,
    BigDecimal pendingPayouts,  // Completed but not yet paid out
    BigDecimal paidOut,
    String currency,
    Map<String, BigDecimal> earningsByMonth  // "2026-01" -> 1500.00
) {
}