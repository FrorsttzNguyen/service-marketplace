package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.VendorDashboardService;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.interfaces.dto.response.VendorEarningsResponse;
import com.hien.marketplace.interfaces.dto.response.VendorStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for vendor dashboard.
 *
 * WHY: Vendors need earnings and booking statistics.
 * Dashboard provides business insights.
 *
 * Implemented in Phase 7 as read-only vendor analytics endpoints.
 */
@RestController
@RequestMapping("/api/vendor/dashboard")
@RequiredArgsConstructor
@Tag(name = "Vendor Dashboard", description = "Vendor analytics and statistics")
@SecurityRequirement(name = "bearerAuth")
public class VendorDashboardController {

    private final VendorDashboardService vendorDashboardService;

    /**
     * Get vendor earnings summary.
     */
    @GetMapping("/earnings")
    @Operation(
            summary = "Get earnings",
            description = "Get earnings summary for authenticated vendor",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Earnings retrieved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a vendor"),
                    @ApiResponse(responseCode = "422", description = "Vendor profile not found")
            }
    )
    public ResponseEntity<VendorEarningsResponse> getEarnings(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(vendorDashboardService.getEarnings(principal.userId()));
    }

    /**
     * Get vendor booking statistics.
     */
    @GetMapping("/stats")
    @Operation(
            summary = "Get statistics",
            description = "Get booking statistics for authenticated vendor",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a vendor"),
                    @ApiResponse(responseCode = "422", description = "Vendor profile not found")
            }
    )
    public ResponseEntity<VendorStatsResponse> getStats(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(vendorDashboardService.getStats(principal.userId()));
    }
}
