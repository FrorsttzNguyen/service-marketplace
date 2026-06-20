package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.ProviderDashboardService;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.interfaces.dto.response.ProviderEarningsResponse;
import com.hien.marketplace.interfaces.dto.response.ProviderStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for provider dashboard.
 *
 * WHY: Providers need earnings and booking statistics.
 * Dashboard provides business insights.
 *
 * Implemented in Phase 7 as read-only provider analytics endpoints.
 */
@RestController
@RequestMapping("/api/provider/dashboard")
@RequiredArgsConstructor
@Tag(name = "Provider Dashboard", description = "Provider analytics and statistics")
@SecurityRequirement(name = "bearerAuth")
public class ProviderDashboardController {

    private final ProviderDashboardService providerDashboardService;

    /**
     * Get provider earnings summary.
     */
    @GetMapping("/earnings")
    @Operation(
            summary = "Get earnings",
            description = "Get earnings summary for authenticated provider",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Earnings retrieved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a provider"),
                    @ApiResponse(responseCode = "422", description = "Provider profile not found")
            }
    )
    public ResponseEntity<ProviderEarningsResponse> getEarnings(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(providerDashboardService.getEarnings(principal.userId()));
    }

    /**
     * Get provider booking statistics.
     */
    @GetMapping("/stats")
    @Operation(
            summary = "Get statistics",
            description = "Get booking statistics for authenticated provider",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a provider"),
                    @ApiResponse(responseCode = "422", description = "Provider profile not found")
            }
    )
    public ResponseEntity<ProviderStatsResponse> getStats(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(providerDashboardService.getStats(principal.userId()));
    }
}
