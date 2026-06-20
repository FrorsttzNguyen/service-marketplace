package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.AdminProviderService;
import com.hien.marketplace.domain.provider.VerificationStatus;
import com.hien.marketplace.interfaces.dto.response.ProviderAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for admin operations (Phase 5.5: provider approval).
 *
 * THIN CONTROLLER: this class only wires HTTP to the application service —
 * no business logic. All status logic lives in {@code AdminProviderService} → {@code Provider} domain.
 *
 * AUTHORIZATION:
 * SecurityConfig already restricts {@code /api/admin/**} to {@code hasRole("ADMIN")}, so every
 * endpoint here is admin-only by URL rule. We add {@code @SecurityRequirement("bearerAuth")} so
 * Swagger UI knows to send the JWT and shows the endpoints as authenticated.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations: provider approval / rejection")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminProviderService adminProviderService;

    /**
     * List providers, optionally filtered by verification status (paginated).
     *
     * @param status optional enum: PENDING | APPROVED | REJECTED (omit for all)
     */
    @GetMapping("/providers")
    @Operation(
            summary = "List providers (optionally filtered by verification status)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of providers"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not an admin")
            }
    )
    public ResponseEntity<Page<ProviderAdminResponse>> listProviders(
            @Parameter(description = "Filter by verification status; omit to list all")
            @RequestParam(name = "status", required = false) VerificationStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // No conversion/validation logic here — just delegate. Unknown enum values are rejected by
        // Spring with 400 before reaching us (VerificationStatus is a real enum in the query param).
        return ResponseEntity.ok(adminProviderService.listProviders(status, pageable));
    }

    /**
     * Approve a PENDING (or already approved) provider.
     */
    @PostMapping("/providers/{providerId}/approve")
    @Operation(
            summary = "Approve a provider",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Provider approved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not an admin"),
                    @ApiResponse(responseCode = "404", description = "Provider not found")
            }
    )
    public ResponseEntity<ProviderAdminResponse> approveProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(adminProviderService.approveProvider(providerId));
    }

    /**
     * Reject a provider.
     */
    @PostMapping("/providers/{providerId}/reject")
    @Operation(
            summary = "Reject a provider",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Provider rejected"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not an admin"),
                    @ApiResponse(responseCode = "404", description = "Provider not found")
            }
    )
    public ResponseEntity<ProviderAdminResponse> rejectProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(adminProviderService.rejectProvider(providerId));
    }
}
