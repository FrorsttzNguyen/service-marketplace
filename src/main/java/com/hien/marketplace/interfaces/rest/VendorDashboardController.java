package com.hien.marketplace.interfaces.rest;

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
 * Note: Full implementation in Phase 3.
 */
@RestController
@RequestMapping("/api/vendor/dashboard")
@RequiredArgsConstructor
@Tag(name = "Vendor Dashboard", description = "Vendor analytics and statistics")
@SecurityRequirement(name = "bearerAuth")
public class VendorDashboardController {

    /**
     * Get vendor earnings summary.
     */
    @GetMapping("/earnings")
    @Operation(
            summary = "Get earnings",
            description = "Get earnings summary for authenticated vendor (Phase 3)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Earnings retrieved")
            }
    )
    public ResponseEntity<Void> getEarnings() {
        // TODO: Phase 3 - Implement
        return ResponseEntity.status(501).build();
    }

    /**
     * Get vendor booking statistics.
     */
    @GetMapping("/stats")
    @Operation(
            summary = "Get statistics",
            description = "Get booking statistics for authenticated vendor (Phase 3)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statistics retrieved")
            }
    )
    public ResponseEntity<Void> getStats() {
        // TODO: Phase 3 - Implement
        return ResponseEntity.status(501).build();
    }
}