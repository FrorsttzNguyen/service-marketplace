package com.hien.marketplace.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the cache is FAIL-SOFT: a cache read/write error must NOT propagate (which would 500 the
 * request). Spring's default SimpleCacheErrorHandler rethrows; this confirms our override swallows so
 * a GET error degrades to a cache miss (recompute) and a corrupt entry self-heals on the next write.
 */
class CacheErrorHandlerTest {

    private final CacheErrorHandler handler = new CacheConfig().errorHandler();

    @Test
    @DisplayName("GET error is swallowed (degrades to a cache miss, not a 500)")
    void getErrorIsSwallowed() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("serviceDetail");

        assertThatCode(() ->
                handler.handleCacheGetError(new RuntimeException("deser failed"), cache, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PUT / EVICT / CLEAR errors are swallowed too")
    void writeSideErrorsAreSwallowed() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("serviceDetail");
        RuntimeException boom = new RuntimeException("redis down");

        assertThat(handler).isNotNull();
        assertThatCode(() -> {
            handler.handleCachePutError(boom, cache, 1L, "value");
            handler.handleCacheEvictError(boom, cache, 1L);
            handler.handleCacheClearError(boom, cache);
        }).doesNotThrowAnyException();
    }
}
