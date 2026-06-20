package com.hien.marketplace.application.service;

import com.hien.marketplace.application.mapper.ServiceMapper;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.infrastructure.persistence.ReviewRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ServiceCatalogService enrichment — specifically that ServiceResponse.totalReviews
 * reflects the REAL review count (the mapper hardcodes it to 0; the service injects the count).
 *
 * Pure Mockito (no Spring), so @Cacheable is a no-op and getServiceById runs enrichment directly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceCatalogServiceTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceMapper serviceMapper;
    @Mock private ReviewRepository reviewRepository;

    private ServiceCatalogService service;

    private static final Long SERVICE_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new ServiceCatalogService(serviceRepository, serviceMapper, reviewRepository);
    }

    private ServiceResponse responseWithReviews(int totalReviews) {
        return new ServiceResponse(
                SERVICE_ID, 1L, "Provider A", 5L, "Cleaning", "Deep Clean", "desc",
                null, new BigDecimal("100.00"), 2, "addr", "city", null,
                ServiceStatus.ACTIVE, new BigDecimal("5.0"), totalReviews, 0, null);
    }

    @Test
    @DisplayName("getServiceById sets totalReviews from the real review count, not the mapper's 0")
    void totalReviewsReflectsRealCount() {
        ServiceEntity entity = org.mockito.Mockito.mock(ServiceEntity.class);
        when(entity.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        when(entity.getId()).thenReturn(SERVICE_ID);
        when(entity.getProvider()).thenReturn(null);
        when(entity.getCategory()).thenReturn(null);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        // The mapper defaults totalReviews to 0 (the bug source).
        when(serviceMapper.toResponse(entity)).thenReturn(responseWithReviews(0));
        // Three reviews exist for this service.
        when(reviewRepository.countByServiceId(SERVICE_ID)).thenReturn(3L);

        ServiceResponse result = service.getServiceById(SERVICE_ID);

        assertThat(result.totalReviews()).isEqualTo(3);
        // The denormalized average is untouched; only the count is enriched.
        assertThat(result.averageRating()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("totalReviews is 0 when a service has no reviews")
    void totalReviewsZeroWhenNoReviews() {
        ServiceEntity entity = org.mockito.Mockito.mock(ServiceEntity.class);
        when(entity.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        when(entity.getId()).thenReturn(SERVICE_ID);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(serviceMapper.toResponse(entity)).thenReturn(responseWithReviews(0));
        when(reviewRepository.countByServiceId(SERVICE_ID)).thenReturn(0L);

        assertThat(service.getServiceById(SERVICE_ID).totalReviews()).isZero();
    }
}
