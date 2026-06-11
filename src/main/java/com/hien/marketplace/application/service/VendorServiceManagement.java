package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.CategoryRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.request.ServiceCreateRequest;
import com.hien.marketplace.interfaces.dto.request.ServiceUpdateRequest;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import com.hien.marketplace.application.mapper.ServiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for vendor to manage their own services.
 *
 * WHY: Vendors have different permissions than customers:
 * - Vendors CREATE/UPDATE/DELETE their own services
 * - Customers only VIEW (public catalog)
 *
 * Authorization: vendorId comes from authenticated user, not request body.
 * This prevents vendors from modifying other vendors' services.
 */
@Service
@RequiredArgsConstructor
public class VendorServiceManagement {

    private final ServiceRepository serviceRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final ServiceMapper serviceMapper;

    /**
     * Get all services owned by a vendor.
     *
     * WHY: Vendor dashboard shows their own services (not all public services).
     * Includes both ACTIVE and INACTIVE services (vendor sees all).
     */
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getVendorServices(Long vendorId, Pageable pageable) {
        return serviceRepository.findByVendorId(vendorId, pageable)
                .map(serviceMapper::toResponse);
    }

    /**
     * Create new service for vendor.
     *
     * Flow:
     * 1. Validate vendor exists and is approved
     * 2. Validate category exists
     * 3. Create ServiceEntity with Money value object
     * 4. Save and return response
     */
    @Transactional
    public ServiceResponse createService(Long vendorId, ServiceCreateRequest request) {
        // Step 1: Validate vendor
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));

        if (!vendor.isApproved()) {
            throw new BusinessRuleViolationException(
                    "Vendor verification",
                    "Vendor must be approved before creating services"
            );
        }

        // Step 2: Validate category
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        // Step 3: Create ServiceEntity
        Money basePrice = Money.of(request.basePrice()
                .multiply(BigDecimal.valueOf(100))  // Convert to cents
                .longValue());

        int durationMinutes = request.durationHours() != null
                ? request.durationHours() * 60
                : 60;  // Default 1 hour

        ServiceEntity service = new ServiceEntity(
                vendor,
                request.title(),
                basePrice,
                request.pricingType(),
                durationMinutes
        );

        service.setCategory(category);
        service.setDescription(request.description());

        // Step 4: Save
        service = serviceRepository.save(service);

        return serviceMapper.toResponse(service);
    }

    /**
     * Update existing service.
     *
     * WHY: Vendors can update title, description, price, etc.
     * Authorization: Only vendor who owns the service can update it.
     */
    @Transactional
    public ServiceResponse updateService(Long vendorId, Long serviceId, ServiceUpdateRequest request) {
        // Step 1: Find service owned by this vendor
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getVendor().getId().equals(vendorId)) {
            throw new BusinessRuleViolationException(
                    "Service ownership",
                    "You can only update your own services"
            );
        }

        // Step 2: Update fields (via domain methods where available)
        if (request.description() != null) {
            service.setDescription(request.description());
        }
        // Note: title, basePrice, durationMinutes need setters or domain methods
        // For now, we skip updating those in Phase 2 - full update in Phase 3

        // Step 3: Save
        service = serviceRepository.save(service);

        return serviceMapper.toResponse(service);
    }

    /**
     * Deactivate service (soft delete).
     *
     * WHY: Vendors can deactivate services instead of deleting.
     * Deactivated services not shown in public catalog.
     */
    @Transactional
    public void deactivateService(Long vendorId, Long serviceId) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getVendor().getId().equals(vendorId)) {
            throw new BusinessRuleViolationException(
                    "Service ownership",
                    "You can only deactivate your own services"
            );
        }

        service.deactivate();  // Domain method
        serviceRepository.save(service);
    }
}