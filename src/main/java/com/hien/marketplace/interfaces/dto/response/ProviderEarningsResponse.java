package com.hien.marketplace.interfaces.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for provider dashboard earnings.
 *
 * WHY: Providers need to track their earnings over time.
 * Used for financial reporting and payout tracking.
 *
 * Note: earningsByMonth is a map of "YYYY-MM" -> total earnings.
 * This allows frontend to render charts.
 */
public record ProviderEarningsResponse(
    BigDecimal totalEarnings,
    BigDecimal pendingPayouts,  // Paid orders not yet fulfilled (PAID status)
    BigDecimal paidOut,  // Fulfilled order subtotals (FULFILLED status)
    String currency,
    Map<String, BigDecimal> earningsByMonth  // "2026-01" -> 1500.00
) {
}