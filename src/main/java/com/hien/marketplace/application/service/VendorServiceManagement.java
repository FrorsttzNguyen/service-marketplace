package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.config.CacheConfig;
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
import org.springframework.cache.annotation.CacheEvict;
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
 * Authorization: userId comes from JWT authentication.
 * Service resolves vendor profile from userId, not from request body.
 * This prevents vendors from modifying other vendors' services.
 *
 * IMPORTANT: userId and vendorId are DIFFERENT database identities.
 * - User: authentication entity (users table)
 * - Vendor: business profile entity (vendors table)
 * - Relationship: Vendor.user references User, but Vendor.id != User.id
 *
 * CACHE EVICTION (Phase 5):
 * - Every mutation (create/update/deactivate) evicts the entire "serviceDetail" cache.
 * - WHY allEntries=true: getServiceById is cached per service id. When a vendor mutates ANY of
 *   their services, we cannot cheaply know which cached ids correspond to that vendor's services
 *   (the cache key is just the id, no vendor scoping). Clearing the whole cache is the safe choice
 *   — it trades a slightly higher miss rate right after a mutation for guaranteed freshness.
 * - WHY beforeInvocation=false (default): eviction runs AFTER the method succeeds. If the method
 *   throws, the DB is unchanged, so the cache MUST stay intact (keeping a valid entry is correct;
 *   evicting it would just cause an unnecessary refill on the next read).
 * - Catalog/category list caches are NOT evicted here because they are not cached in the first
 *   place (see ServiceCatalogService javadoc for the Page<T> rationale).
 */
@Service
@RequiredArgsConstructor
public class VendorServiceManagement {

    private final ServiceRepository serviceRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final ServiceMapper serviceMapper;

    /**
     * Resolve vendor profile from authenticated user ID.
     *
     * WHY: userId from JWT is NOT the same as vendorId.
     * We need to look up the vendor profile that belongs to this user.
     *
     * @param userId Authenticated user ID from JWT
     * @return Vendor profile
     * @throws BusinessRuleViolationException if user has no vendor profile
     */
    private Vendor getVendorByUserId(Long userId) {
        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Vendor profile",
                        "Vendor profile not found. Please complete vendor registration."
                ));
    }

    /**
     * Get all services owned by a vendor.
     *
     * WHY: Vendor dashboard shows their own services (not all public services).
     * Includes both ACTIVE and INACTIVE services (vendor sees all).
     *
     * @param userId Authenticated user ID (resolved to vendor internally)
     */
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getVendorServices(Long userId, Pageable pageable) {
        Vendor vendor = getVendorByUserId(userId);
        return serviceRepository.findByVendorId(vendor.getId(), pageable)
                .map(serviceMapper::toResponse);
    }

    /**
     * Create new service for vendor.
     *
     * Flow:
     * 1. Resolve vendor profile from userId
     * 2. Validate vendor is approved
     * 3. Validate category exists
     * 4. Create ServiceEntity with Money value object
     * 5. Save and return response
     *
     * @param userId Authenticated user ID (resolved to vendor internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public ServiceResponse createService(Long userId, ServiceCreateRequest request) {
        // Step 1: Resolve vendor profile
        Vendor vendor = getVendorByUserId(userId);

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
     *
     * @param userId Authenticated user ID (resolved to vendor internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public ServiceResponse updateService(Long userId, Long serviceId, ServiceUpdateRequest request) {
        // Step 1: Resolve vendor profile
        Vendor vendor = getVendorByUserId(userId);

        // Step 2: Find service owned by this vendor
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getVendor().getId().equals(vendor.getId())) {
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
     *
     * @param userId Authenticated user ID (resolved to vendor internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public void deactivateService(Long userId, Long serviceId) {
        // Resolve vendor profile
        Vendor vendor = getVendorByUserId(userId);

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getVendor().getId().equals(vendor.getId())) {
            throw new BusinessRuleViolationException(
                    "Service ownership",
                    "You can only deactivate your own services"
            );
        }

        service.deactivate();  // Domain method
        serviceRepository.save(service);
    }

    /**
     * Activate (publish) a service so it appears in the public catalog.
     *
     * WHY: a service is created as DRAFT, and the public catalog only lists ACTIVE services. Without
     * this, a freshly created service could never become visible — this is the symmetric counterpart
     * of deactivateService. The domain method ServiceEntity#activate() owns the transition.
     *
     * Authorization: only the vendor who owns the service may activate it (mirrors the other mutations).
     *
     * @param userId Authenticated user ID (resolved to vendor internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public ServiceResponse activateService(Long userId, Long serviceId) {
        Vendor vendor = getVendorByUserId(userId);

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getVendor().getId().equals(vendor.getId())) {
            throw new BusinessRuleViolationException(
                    "Service ownership",
                    "You can only activate your own services"
            );
        }

        service.activate();  // Domain method: DRAFT/INACTIVE -> ACTIVE
        service = serviceRepository.save(service);
        return serviceMapper.toResponse(service);
    }
}