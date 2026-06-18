package com.hien.marketplace.config;

import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the Redis cache value serializer (RedisConfig#cacheJsonSerializer).
 *
 * WHY this exists: the @Cacheable path stores DTOs in Redis as JSON with embedded "@class" type
 * info. A previous config built the serializer from a hand-typed ObjectMapper, which wrote
 * ASYMMETRIC JSON (no root "@class") and made EVERY cache HIT fail on read with
 * "missing type id property '@class'" → HTTP 500. The existing ServiceCatalogCachingTest uses the
 * in-memory ConcurrentMapCacheManager (no serialization), so it could never catch this. This test
 * exercises the EXACT serializer the production cache uses, round-tripping the trickiest payload:
 * a record with non-null BigDecimal fields (basePrice + averageRating) and a java.time field.
 */
class RedisCacheSerializationTest {

    @Test
    @DisplayName("ServiceResponse with BigDecimal + java.time round-trips through the cache serializer")
    void serviceResponseRoundTrips() {
        GenericJackson2JsonRedisSerializer serializer = RedisConfig.cacheJsonSerializer();

        ServiceResponse original = new ServiceResponse(
                1L, 1L, "Vendor Biz", 2L, "Cleaning", "Deep Home Cleaning", "Thorough clean",
                PricingType.FIXED, new BigDecimal("100.00"), 2, "1 Main St", "Metropolis", null,
                ServiceStatus.ACTIVE, new BigDecimal("5.0"), 1, 0,
                LocalDateTime.of(2026, 6, 18, 10, 30, 0));

        byte[] bytes = serializer.serialize(original);
        Object back = serializer.deserialize(bytes);

        // Must reconstruct as the concrete type (proves the root "@class" is present) ...
        assertThat(back).isInstanceOf(ServiceResponse.class);
        ServiceResponse result = (ServiceResponse) back;
        // ... and preserve the values, including the non-null BigDecimals that triggered the bug.
        assertThat(result).isEqualTo(original);
        assertThat(result.averageRating()).isEqualByComparingTo("5.0");
        assertThat(result.basePrice()).isEqualByComparingTo("100.00");
    }
}
