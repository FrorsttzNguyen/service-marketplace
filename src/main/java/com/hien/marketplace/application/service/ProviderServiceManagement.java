package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.config.CacheConfig;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.infrastructure.persistence.CategoryRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.ProviderRepository;
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
 * Service for provider to manage their own services.
 *
 * WHY: Providers have different permissions than customers:
 * - Providers CREATE/UPDATE/DELETE their own services
 * - Customers only VIEW (public catalog)
 *
 * Authorization: userId comes from JWT authentication.
 * Service resolves provider profile from userId, not from request body.
 * This prevents providers from modifying other providers' services.
 *
 * IMPORTANT: userId and providerId are DIFFERENT database identities.
 * - User: authentication entity (users table)
 * - Provider: business profile entity (providers table)
 * - Relationship: Provider.user references User, but Provider.id != User.id
 *
 * CACHE EVICTION (Phase 5):
 * - Every mutation (create/update/deactivate) evicts the entire "serviceDetail" cache.
 * - WHY allEntries=true: getServiceById is cached per service id. When a provider mutates ANY of
 *   their services, we cannot cheaply know which cached ids correspond to that provider's services
 *   (the cache key is just the id, no provider scoping). Clearing the whole cache is the safe choice
 *   — it trades a slightly higher miss rate right after a mutation for guaranteed freshness.
 * - WHY beforeInvocation=false (default): eviction runs AFTER the method succeeds. If the method
 *   throws, the DB is unchanged, so the cache MUST stay intact (keeping a valid entry is correct;
 *   evicting it would just cause an unnecessary refill on the next read).
 * - Catalog/category list caches are NOT evicted here because they are not cached in the first
 *   place (see ServiceCatalogService javadoc for the Page<T> rationale).
 */
@Service
@RequiredArgsConstructor
public class ProviderServiceManagement {

    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;
    private final CategoryRepository categoryRepository;
    private final ServiceMapper serviceMapper;

    /**
     * Resolve provider profile from authenticated user ID.
     *
     * WHY: userId from JWT is NOT the same as providerId.
     * We need to look up the provider profile that belongs to this user.
     *
     * @param userId Authenticated user ID from JWT
     * @return Provider profile
     * @throws BusinessRuleViolationException if user has no provider profile
     */
    private Provider getProviderByUserId(Long userId) {
        return providerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Provider profile",
                        "Provider profile not found. Please complete provider registration."
                ));
    }

    /**
     * Get all services owned by a provider.
     *
     * WHY: Provider dashboard shows their own services (not all public services).
     * Includes both ACTIVE and INACTIVE services (provider sees all).
     *
     * @param userId Authenticated user ID (resolved to provider internally)
     */
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getProviderServices(Long userId, Pageable pageable) {
        Provider provider = getProviderByUserId(userId);
        return serviceRepository.findByProviderId(provider.getId(), pageable)
                .map(serviceMapper::toResponse);
    }

    /**
     * Create new service for provider.
     *
     * Flow:
     * 1. Resolve provider profile from userId
     * 2. Validate provider is approved
     * 3. Validate category exists
     * 4. Create ServiceEntity with Money value object
     * 5. Save and return response
     *
     * @param userId Authenticated user ID (resolved to provider internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public ServiceResponse createService(Long userId, ServiceCreateRequest request) {
        // Step 1: Resolve provider profile
        Provider provider = getProviderByUserId(userId);

        if (!provider.isApproved()) {
            throw new BusinessRuleViolationException(
                    "Provider verification",
                    "Provider must be approved before creating services"
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
                provider,
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
     * WHY: Providers can update title, description, price, etc.
     * Authorization: Only provider who owns the service can update it.
     *
     * @param userId Authenticated user ID (resolved to provider internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public ServiceResponse updateService(Long userId, Long serviceId, ServiceUpdateRequest request) {
        // Step 1: Resolve provider profile
        Provider provider = getProviderByUserId(userId);

        // Step 2: Find service owned by this provider
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getProvider().getId().equals(provider.getId())) {
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
     * WHY: Providers can deactivate services instead of deleting.
     * Deactivated services not shown in public catalog.
     *
     * @param userId Authenticated user ID (resolved to provider internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public void deactivateService(Long userId, Long serviceId) {
        // Resolve provider profile
        Provider provider = getProviderByUserId(userId);

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getProvider().getId().equals(provider.getId())) {
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
     * Authorization: only the provider who owns the service may activate it (mirrors the other mutations).
     *
     * @param userId Authenticated user ID (resolved to provider internally)
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, allEntries = true,
            beforeInvocation = false)
    public ServiceResponse activateService(Long userId, Long serviceId) {
        Provider provider = getProviderByUserId(userId);

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getProvider().getId().equals(provider.getId())) {
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