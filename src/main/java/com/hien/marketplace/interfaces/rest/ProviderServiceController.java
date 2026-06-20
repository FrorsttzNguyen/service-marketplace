package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.ProviderServiceManagement;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.interfaces.dto.request.ServiceCreateRequest;
import com.hien.marketplace.interfaces.dto.request.ServiceUpdateRequest;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for provider service management.
 *
 * WHY: Providers manage their own services - different from public catalog.
 * All endpoints require VENDOR role authentication.
 *
 * Security:
 * - @AuthenticationPrincipal extracts userId from JWT token
 * - Service layer resolves provider profile from userId
 * - providerId is NOT the same as userId (different database identities)
 */
@RestController
@RequestMapping("/api/provider/services")
@RequiredArgsConstructor
@Tag(name = "Provider Service Management", description = "Endpoints for providers to manage their services")
@SecurityRequirement(name = "bearerAuth")
public class ProviderServiceController {

    private final ProviderServiceManagement providerServiceManagement;

    /**
     * Get all services owned by authenticated provider.
     *
     * Note: userId is resolved to provider profile internally.
     */
    @GetMapping
    @Operation(
            summary = "Get provider's services",
            description = "List all services owned by the authenticated provider",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Services retrieved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a provider")
            }
    )
    public ResponseEntity<Page<ServiceResponse>> getMyServices(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ServiceResponse> services = providerServiceManagement.getProviderServices(
                principal.userId(),
                pageable
        );
        return ResponseEntity.ok(services);
    }

    /**
     * Create new service.
     */
    @PostMapping
    @Operation(
            summary = "Create service",
            description = "Create a new service for the authenticated provider",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Service created"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a provider"),
                    @ApiResponse(responseCode = "422", description = "Provider not approved")
            }
    )
    public ResponseEntity<ServiceResponse> createService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody ServiceCreateRequest request
    ) {
        ServiceResponse service = providerServiceManagement.createService(
                principal.userId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(service);
    }

    /**
     * Update existing service.
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update service",
            description = "Update an existing service owned by the authenticated provider",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service updated"),
                    @ApiResponse(responseCode = "404", description = "Service not found"),
                    @ApiResponse(responseCode = "422", description = "Not your service")
            }
    )
    public ResponseEntity<ServiceResponse> updateService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ServiceUpdateRequest request
    ) {
        ServiceResponse service = providerServiceManagement.updateService(
                principal.userId(),
                id,
                request
        );
        return ResponseEntity.ok(service);
    }

    /**
     * Deactivate service.
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Deactivate service",
            description = "Deactivate (soft delete) a service owned by the authenticated provider",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Service deactivated"),
                    @ApiResponse(responseCode = "404", description = "Service not found"),
                    @ApiResponse(responseCode = "422", description = "Not your service")
            }
    )
    public ResponseEntity<Void> deactivateService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        providerServiceManagement.deactivateService(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activate (publish) a service.
     *
     * A service is created as DRAFT and the public catalog only lists ACTIVE services, so a provider
     * needs this to make a service visible. Symmetric counterpart of deactivate.
     */
    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate service",
            description = "Activate (publish) a service owned by the authenticated provider so it appears in the public catalog",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service activated"),
                    @ApiResponse(responseCode = "404", description = "Service not found"),
                    @ApiResponse(responseCode = "422", description = "Not your service")
            }
    )
    public ResponseEntity<ServiceResponse> activateService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        ServiceResponse service = providerServiceManagement.activateService(principal.userId(), id);
        return ResponseEntity.ok(service);
    }
}