package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

/**
 * Repository for ServiceEntity.
 *
 * WHY JpaSpecificationExecutor?
 * - Enables dynamic query building with Specification pattern
 * - Used for ServiceSearchRequest filtering (by category, price range, rating, etc.)
 * - Avoids hardcoding query methods for every combination
 *
 * WHY @EntityGraph?
 * - Prevents N+1 queries when fetching services with provider/category
 * - provider and category have FetchType.LAZY by default
 * - EntityGraph eagerly fetches them in ONE query
 */
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long>,
                                              JpaSpecificationExecutor<ServiceEntity> {

    /**
     * Find services by provider ID with provider and category preloaded.
     * Prevents N+1 queries when mapping to ServiceResponse.
     */
    @EntityGraph(value = "service-with-provider-category", type = EntityGraph.EntityGraphType.LOAD)
    Page<ServiceEntity> findByProviderId(Long providerId, Pageable pageable);

    List<ServiceEntity> findByProviderId(Long providerId);

    long countByProviderId(Long providerId);

    long countByProviderIdAndStatus(Long providerId, ServiceStatus status);

    List<ServiceEntity> findByCategoryId(Long categoryId);

    List<ServiceEntity> findByStatus(ServiceStatus status);

    /**
     * Find active services with provider and category preloaded.
     * Used for public service catalog.
     */
    @EntityGraph(value = "service-with-provider-category", type = EntityGraph.EntityGraphType.LOAD)
    Page<ServiceEntity> findByStatus(ServiceStatus status, Pageable pageable);

    List<ServiceEntity> findByCategoryIdAndStatus(Long categoryId, ServiceStatus status);

    /**
     * Find active services by category with provider and category preloaded.
     * Used for category filtering in catalog.
     */
    @EntityGraph(value = "service-with-provider-category", type = EntityGraph.EntityGraphType.LOAD)
    Page<ServiceEntity> findByCategoryIdAndStatus(Long categoryId, ServiceStatus status, Pageable pageable);

    List<ServiceEntity> findByProviderIdAndStatus(Long providerId, ServiceStatus status);
}
