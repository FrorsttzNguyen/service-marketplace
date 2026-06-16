package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.ServiceMapper;
import com.hien.marketplace.config.CacheConfig;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
 *
 * CACHING STRATEGY (Phase 5):
 * - getServiceById() IS cached: it returns a concrete ServiceResponse record, which Jackson can
 *   serialize/deserialize cleanly through the RedisCacheManager's JSON serializer.
 * - getAllServices() and getServicesByCategory() are NOT cached here, because they return Page<T>.
 *   Page is a Spring Data interface; there is no Jackson module on the classpath that can
 *   reconstruct a PageImpl from JSON (the official jackson-datatype-spring-data artifact does not
 *   resolve on Maven Central for this stack). Caching Page blindly would store successfully on a
 *   cache MISS but deserialize into a LinkedHashMap on a HIT — a silent ClassCastException later.
 *   See docs/html/{vi,en}/phase5/02-cache-invalidation.html "When NOT to cache" for the rationale.
 *
 * PROXY RULE: @Cacheable only works when the method is invoked THROUGH a Spring proxy (i.e. from
 * another bean). These methods are all called from ServiceController, so the proxy is active.
 * Never call a @Cacheable method via `this.` (self-invocation) — the annotation would be skipped.
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
     *
     * NOT CACHED — see class javadoc: Page<T> cannot be safely JSON-deserialized on a cache hit.
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
     *
     * CACHED in Redis under cache name "serviceDetail", keyed by the service id.
     * - Cache MISS → runs the query + enrichment, stores the ServiceResponse.
     * - Cache HIT → returns the cached ServiceResponse, no DB access.
     * - Evicted by VendorServiceManagement.updateService / deactivateService (see @CacheEvict there).
     *
     * Condition `unless`: a null result (handled by the orElseThrow above) is never cached — but
     * since the method throws rather than returning null, this is a defensive guard only.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_SERVICE_DETAIL, key = "#id")
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
     *
     * NOT CACHED — see class javadoc: Page<T> cannot be safely JSON-deserialized on a cache hit.
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
     * This method extracts those names after mapping.
     *
     * N+1 FIXED: ServiceRepository methods now use @EntityGraph
     * to eagerly fetch vendor and category in ONE query.
     *
     * NOTE: city and averageRating are now directly mapped from ServiceEntity
     * (denormalized fields added in V8 migration).
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