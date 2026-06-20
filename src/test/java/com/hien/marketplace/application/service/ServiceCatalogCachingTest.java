package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.ServiceMapper;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.ProviderRepository;
import com.hien.marketplace.interfaces.dto.request.ServiceUpdateRequest;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that the @Cacheable behavior on ServiceCatalogService actually reduces DB load.
 *
 * STRATEGY:
 * - Run with the "test" profile → spring.cache.type=simple → an in-JVM ConcurrentMapCacheManager.
 *   This isolates cache LOGIC from Redis, so we don't need a Redis server to prove the cache works.
 * - MockBean ServiceRepository so we can COUNT how many times the underlying query runs.
 *   A real cache hit must result in ZERO additional repository calls.
 *
 * WHAT THESE TESTS PROVE:
 * 1. First call hits the repository (cache MISS).
 * 2. Second call does NOT hit the repository (cache HIT).
 * 3. A successful @CacheEvict mutation clears the entry.
 * 4. A failed @CacheEvict mutation keeps the entry because beforeInvocation=false.
 * 5. A thrown result (404) is not cached, so a retry re-queries.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceCatalogCachingTest {

    @Autowired
    private ServiceCatalogService serviceCatalogService;

    @Autowired
    private ProviderServiceManagement providerServiceManagement;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ServiceRepository serviceRepository;

    @MockBean
    private ProviderRepository providerRepository;

    // ServiceMapper is a MapStruct bean; we also mock it to avoid needing full entity graphs,
    // and so enrichServiceResponse rebuilds the response deterministically.
    @MockBean
    private ServiceMapper serviceMapper;

    private ServiceEntity sampleService;
    private ServiceResponse sampleResponse;

    @BeforeEach
    void setUp() {
        // Clear the cache between tests so each test starts from a known empty state.
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });

        // ServiceEntity has package-private/no public setters for id+status by design (domain-rich).
        // For a cache test we only care that getServiceById resolves and caches, so a Mockito stub
        // of the entity is sufficient and avoids constructing a full Provider/Money/PricingType graph.
        sampleService = org.mockito.Mockito.mock(ServiceEntity.class);
        org.mockito.Mockito.when(sampleService.getStatus()).thenReturn(ServiceStatus.ACTIVE);

        sampleResponse = new ServiceResponse(
                1L, 10L, "Provider A", 5L, "Category X", "Title", "Desc", null, null, null,
                null, "Hanoi", null, ServiceStatus.ACTIVE, null, 0, 0, null
        );

        // Default mapping behavior used by enrichServiceResponse.
        when(serviceMapper.toResponse(sampleService)).thenReturn(sampleResponse);
    }

    @Test
    @DisplayName("Second getServiceById call should NOT hit the repository (cache HIT)")
    void secondCallShouldHitCache() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(sampleService));

        serviceCatalogService.getServiceById(1L);
        serviceCatalogService.getServiceById(1L);

        // Only ONE DB query across two service calls.
        verify(serviceRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("First call is a cache MISS (repository is queried once)")
    void firstCallQueriesRepository() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(sampleService));

        ServiceResponse result = serviceCatalogService.getServiceById(1L);

        assertThat(result).isNotNull();
        verify(serviceRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Different ids cache independently")
    void differentIdsCacheSeparately() {
        ServiceEntity svc2 = org.mockito.Mockito.mock(ServiceEntity.class);
        org.mockito.Mockito.when(svc2.getStatus()).thenReturn(ServiceStatus.ACTIVE);
        // enrichServiceResponse maps every entity → response (MapStruct never returns null in prod),
        // so the second entity needs a non-null mapping too.
        when(serviceMapper.toResponse(svc2)).thenReturn(sampleResponse);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(sampleService));
        when(serviceRepository.findById(2L)).thenReturn(Optional.of(svc2));

        serviceCatalogService.getServiceById(1L);
        serviceCatalogService.getServiceById(2L);
        serviceCatalogService.getServiceById(1L); // cache hit for id=1
        serviceCatalogService.getServiceById(2L); // cache hit for id=2

        verify(serviceRepository, times(1)).findById(1L);
        verify(serviceRepository, times(1)).findById(2L);
    }

    @Test
    @DisplayName("After a successful provider update, the cached detail is evicted")
    void successfulUpdateServiceEvictsCache() {
        Provider provider = mock(Provider.class);
        when(provider.getId()).thenReturn(10L);
        when(providerRepository.findByUserId(99L)).thenReturn(Optional.of(provider));
        when(sampleService.getProvider()).thenReturn(provider);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(sampleService));
        when(serviceRepository.save(sampleService)).thenReturn(sampleService);

        serviceCatalogService.getServiceById(1L); // populate cache
        assertThat(cacheManager.getCache("serviceDetail").get(1L)).isNotNull();

        ServiceUpdateRequest request = new ServiceUpdateRequest(
                null, "Updated description", null, null, null, null, null, null, null
        );
        providerServiceManagement.updateService(99L, 1L, request);

        assertThat(cacheManager.getCache("serviceDetail").get(1L))
                .as("successful mutation should clear stale service details")
                .isNull();
        verify(serviceRepository, times(1)).save(sampleService);
    }

    @Test
    @DisplayName("Failed provider update keeps the cached detail")
    void failedUpdateDoesNotEvictCache() {
        // The method throws before a successful mutation, and because beforeInvocation=false,
        // @CacheEvict does NOT run. Keeping the cache entry is correct because the DB was unchanged.
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(sampleService));
        serviceCatalogService.getServiceById(1L); // populate cache
        assertThat(cacheManager.getCache("serviceDetail").get(1L)).isNotNull();

        // updateService will throw because the provider lookup has no provider profile in this mocked
        // context; the transaction/method fails → @CacheEvict(beforeInvocation=false) does NOT run.
        assertThatThrownBy(() -> providerServiceManagement.updateService(999L, 1L, null))
                .isInstanceOf(RuntimeException.class);

        // Cache entry still present because the mutation did not succeed.
        assertThat(cacheManager.getCache("serviceDetail").get(1L))
                .as("cache must NOT be evicted when the mutating method throws (beforeInvocation=false)")
                .isNotNull();
    }

    @Test
    @DisplayName("A 404 result is not cached — retry re-queries the repository")
    void notFoundIsNotCached() {
        when(serviceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceCatalogService.getServiceById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> serviceCatalogService.getServiceById(404L))
                .isInstanceOf(ResourceNotFoundException.class);

        // Two calls → two queries, because the exception path is not cached.
        verify(serviceRepository, times(2)).findById(404L);
    }
}
