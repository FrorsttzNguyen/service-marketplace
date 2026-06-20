package com.hien.marketplace.infrastructure.persistence.specification;

import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.interfaces.dto.request.ServiceSearchRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Specifications for ServiceEntity queries.
 *
 * WHY Specification Pattern?
 * - Dynamic query building based on optional filters
 * - Combines multiple filters without hardcoding every combination
 * - Clean, testable, reusable
 *
 * USAGE:
 * <pre>
 * {@code
 * ServiceSearchRequest request = new ServiceSearchRequest(...);
 * Specification<ServiceEntity> spec = ServiceSpecification.fromRequest(request);
 * Page<ServiceEntity> results = serviceRepository.findAll(spec, pageable);
 * }
 * </pre>
 */
public class ServiceSpecification {

    /**
     * Build Specification from search request.
     *
     * Each filter is optional and only applied if the corresponding field is set.
     * All filters are combined with AND logic.
     *
     * @param request Search parameters
     * @return Specification for dynamic query
     */
    public static Specification<ServiceEntity> fromRequest(ServiceSearchRequest request) {
        return Specification
                .where(hasStatus(ServiceStatus.ACTIVE))  // Always filter for active services
                .and(hasKeyword(request.keyword()))
                .and(hasCategory(request.categoryId()))
                .and(hasProvider(request.providerId()))
                .and(hasCity(request.city()))
                .and(hasMinRating(request.minRating()))
                .and(hasPriceRange(request.minPrice(), request.maxPrice()));
    }

    /**
     * Filter by status.
     *
     * WHY: Public catalog only shows ACTIVE services.
     */
    public static Specification<ServiceEntity> hasStatus(ServiceStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Search by keyword in name and description.
     *
     * WHY: Case-insensitive partial match for better UX.
     */
    public static Specification<ServiceEntity> hasKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    /**
     * Filter by category.
     */
    public static Specification<ServiceEntity> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /**
     * Filter by provider.
     */
    public static Specification<ServiceEntity> hasProvider(Long providerId) {
        if (providerId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("provider").get("id"), providerId);
    }

    /**
     * Filter by price range.
     *
     * WHY: Uses basePrice.amountCents (embedded Money value object).
     */
    public static Specification<ServiceEntity> hasPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return null;
        }

        return (root, query, cb) -> {
            // Convert BigDecimal to cents (multiply by 100)
            if (minPrice != null && maxPrice != null) {
                long minCents = minPrice.multiply(BigDecimal.valueOf(100)).longValue();
                long maxCents = maxPrice.multiply(BigDecimal.valueOf(100)).longValue();
                return cb.between(root.get("basePrice").get("amountCents"), minCents, maxCents);
            } else if (minPrice != null) {
                long minCents = minPrice.multiply(BigDecimal.valueOf(100)).longValue();
                return cb.ge(root.get("basePrice").get("amountCents"), minCents);
            } else {
                long maxCents = maxPrice.multiply(BigDecimal.valueOf(100)).longValue();
                return cb.le(root.get("basePrice").get("amountCents"), maxCents);
            }
        };
    }

    /**
     * Filter by city.
     *
     * WHY: City is denormalized onto services table for efficient filtering.
     * Case-insensitive partial match for better UX.
     */
    public static Specification<ServiceEntity> hasCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        // Case-insensitive match (city stored as-is from provider address)
        return (root, query, cb) -> cb.equal(cb.lower(root.get("city")), city.toLowerCase());
    }

    /**
     * Filter by minimum rating.
     *
     * WHY: averageRating is denormalized onto services table.
     * Services with no reviews (NULL averageRating) are excluded when filtering by rating.
     *
     * @param minRating Minimum rating threshold (1-5)
     */
    public static Specification<ServiceEntity> hasMinRating(Integer minRating) {
        if (minRating == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("averageRating")),
                        cb.ge(root.get("averageRating"), minRating)
                );
    }
}
