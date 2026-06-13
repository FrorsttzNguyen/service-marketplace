package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.ServiceCatalogService;
import com.hien.marketplace.interfaces.dto.request.ServiceSearchRequest;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for public service catalog.
 *
 * WHY: Exposes public endpoints for browsing services.
 * No authentication required - anyone can browse the catalog.
 *
 * Endpoints:
 * - GET /api/services - List all active services (paginated)
 * - GET /api/services/search - Search services with filters
 * - GET /api/services/{id} - Get service detail
 * - GET /api/services/category/{categoryId} - Filter by category
 */
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Service Catalog", description = "Public service browsing endpoints")
public class ServiceController {

    private final ServiceCatalogService serviceCatalogService;

    /**
     * List all active services with pagination.
     *
     * Pagination: ?page=0&size=20&sort=createdAt,desc
     * Default: page 0, size 20, sort by createdAt descending
     */
    @GetMapping
    @Operation(
            summary = "List all services",
            description = "Get paginated list of all active services",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Services retrieved successfully")
            }
    )
    public ResponseEntity<Page<ServiceResponse>> getAllServices(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<ServiceResponse> services = serviceCatalogService.getAllServices(pageable);
        return ResponseEntity.ok(services);
    }

    /**
     * Search services with filters.
     *
     * All filters are optional - client can filter by any combination.
     * Spring automatically binds query params to ServiceSearchRequest fields.
     *
     * Query params:
     * - keyword: Search in name and description
     * - categoryId: Filter by category
     * - vendorId: Filter by vendor
     * - city: Filter by city
     * - minPrice / maxPrice: Price range in dollars (converted to cents internally)
     * - minRating: Minimum rating (1-5)
     * - page, size, sort: Pagination (handled by Pageable)
     *
     * IMPORTANT: This endpoint must come BEFORE getServiceById(@PathVariable Long id)
     * to avoid path conflict. Spring MVC matches "/api/services/search" before "/api/services/{id}".
     */
    @GetMapping("/search")
    @Operation(
            summary = "Search services",
            description = "Search and filter services by keyword, category, vendor, city, price range, and rating",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
            }
    )
    public ResponseEntity<Page<ServiceResponse>> searchServices(
            @Valid ServiceSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<ServiceResponse> services = serviceCatalogService.searchServices(request, pageable);
        return ResponseEntity.ok(services);
    }

    /**
     * Get service detail by ID.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get service detail",
            description = "Get detailed information about a specific service",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service found"),
                    @ApiResponse(responseCode = "404", description = "Service not found")
            }
    )
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable Long id) {
        ServiceResponse service = serviceCatalogService.getServiceById(id);
        return ResponseEntity.ok(service);
    }

    /**
     * Get services by category.
     */
    @GetMapping("/category/{categoryId}")
    @Operation(
            summary = "Get services by category",
            description = "Filter services by category ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Services retrieved successfully")
            }
    )
    public ResponseEntity<Page<ServiceResponse>> getServicesByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ServiceResponse> services = serviceCatalogService.getServicesByCategory(categoryId, pageable);
        return ResponseEntity.ok(services);
    }
}