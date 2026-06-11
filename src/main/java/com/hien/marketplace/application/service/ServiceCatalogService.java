package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.ServiceMapper;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for public service catalog operations.
 *
 * WHY: Separates public catalog operations from vendor service management.
 * - Customers browse services via this service
 * - Vendors manage their services via VendorServiceManagement
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;

    /**
     * Get all active services with pagination.
     *
     * WHY: Public catalog shows only ACTIVE services.
     * Pageable provides offset-based pagination (page, size).
     */
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getAllServices(Pageable pageable) {
        return serviceRepository.findByStatus(ServiceStatus.ACTIVE, pageable)
                .map(this::enrichServiceResponse);
    }

    /**
     * Get service by ID (public view).
     *
     * WHY: Customers can view any active service.
     * Returns 404 if service not found or inactive.
     */
    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));

        return enrichServiceResponse(service);
    }

    /**
     * Search services by category.
     *
     * WHY: Customers filter services by category.
     * Only returns ACTIVE services.
     */
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getServicesByCategory(Long categoryId, Pageable pageable) {
        return serviceRepository.findByCategoryIdAndStatus(categoryId, ServiceStatus.ACTIVE, pageable)
                .map(this::enrichServiceResponse);
    }

    /**
     * Enrich ServiceResponse with vendor name and category name.
     *
     * WHY: ServiceResponse needs vendorName and categoryName
     * but ServiceEntity only has references (vendor, category).
     * This method fetches those names after mapping.
     *
     * Note: In production, this would cause N+1 queries.
     * Phase 3 will add @EntityGraph or custom JOIN query.
     */
    private ServiceResponse enrichServiceResponse(ServiceEntity service) {
        ServiceResponse response = serviceMapper.toResponse(service);

        // Enrich with vendor name
        if (service.getVendor() != null && service.getVendor().getUser() != null) {
            response = new ServiceResponse(
                    response.id(),
                    response.vendorId(),
                    service.getVendor().getBusinessName(),
                    response.categoryId(),
                    response.categoryName(),
                    response.title(),
                    response.description(),
                    response.pricingType(),
                    response.basePrice(),
                    response.durationHours(),
                    response.address(),
                    response.city(),
                    response.imageUrl(),
                    response.status(),
                    response.averageRating(),
                    response.totalReviews(),
                    response.totalBookings(),
                    response.createdAt()
            );
        }

        // Enrich with category name
        if (service.getCategory() != null) {
            response = new ServiceResponse(
                    response.id(),
                    response.vendorId(),
                    response.vendorName(),
                    response.categoryId(),
                    service.getCategory().getName(),
                    response.title(),
                    response.description(),
                    response.pricingType(),
                    response.basePrice(),
                    response.durationHours(),
                    response.address(),
                    response.city(),
                    response.imageUrl(),
                    response.status(),
                    response.averageRating(),
                    response.totalReviews(),
                    response.totalBookings(),
                    response.createdAt()
            );
        }

        return response;
    }
}