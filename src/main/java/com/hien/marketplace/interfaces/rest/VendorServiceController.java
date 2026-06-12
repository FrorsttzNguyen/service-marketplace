package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.VendorServiceManagement;
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
 * REST controller for vendor service management.
 *
 * WHY: Vendors manage their own services - different from public catalog.
 * All endpoints require VENDOR role authentication.
 *
 * Security:
 * - @AuthenticationPrincipal extracts userId from JWT token
 * - Service layer resolves vendor profile from userId
 * - vendorId is NOT the same as userId (different database identities)
 */
@RestController
@RequestMapping("/api/vendor/services")
@RequiredArgsConstructor
@Tag(name = "Vendor Service Management", description = "Endpoints for vendors to manage their services")
@SecurityRequirement(name = "bearerAuth")
public class VendorServiceController {

    private final VendorServiceManagement vendorServiceManagement;

    /**
     * Get all services owned by authenticated vendor.
     *
     * Note: userId is resolved to vendor profile internally.
     */
    @GetMapping
    @Operation(
            summary = "Get vendor's services",
            description = "List all services owned by the authenticated vendor",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Services retrieved"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a vendor")
            }
    )
    public ResponseEntity<Page<ServiceResponse>> getMyServices(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ServiceResponse> services = vendorServiceManagement.getVendorServices(
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
            description = "Create a new service for the authenticated vendor",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Service created"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "403", description = "Not a vendor"),
                    @ApiResponse(responseCode = "422", description = "Vendor not approved")
            }
    )
    public ResponseEntity<ServiceResponse> createService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody ServiceCreateRequest request
    ) {
        ServiceResponse service = vendorServiceManagement.createService(
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
            description = "Update an existing service owned by the authenticated vendor",
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
        ServiceResponse service = vendorServiceManagement.updateService(
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
            description = "Deactivate (soft delete) a service owned by the authenticated vendor",
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
        vendorServiceManagement.deactivateService(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}