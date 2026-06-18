package com.hien.marketplace.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache abstraction configuration backed by Redis (Phase 5).
 *
 * WHY @EnableCaching:
 * - Without it, @Cacheable / @CacheEvict / @CachePut annotations are SILENTLY IGNORED.
 * - This is the #1 "my cache doesn't work" mistake: dependencies + annotations but no @EnableCaching.
 *
 * WHY RedisCacheManager (not the generic ConcurrentMapCacheManager):
 * - ConcurrentMapCacheManager stores cache in the JVM heap → lost on restart, NOT shared across
 *   instances, and consumes app memory.
 * - RedisCacheManager stores cache in Redis → survives restarts, shared by all app instances,
 *   and lets us set a TTL (expiration) per cache name.
 *
 * CACHE-ASIDE PATTERN (what @Cacheable implements):
 * 1. Method is called with args (e.g. getServiceById(1)).
 * 2. Spring computes a cache key from the args ("serviceDetail::1").
 * 3. If Redis has an entry → return it, the method body NEVER runs (no DB hit).
 * 4. If miss → run the method, store the return value in Redis, then return it.
 *
 * TTL STRATEGY (why per-cache TTL):
 * - Different data changes at different rates. A blanket TTL forces a tradeoff that's wrong for
 *   everything.
 * - serviceCatalog (listings): changes when a vendor creates/updates/deactivates a service.
 *   Vendors don't do this every second → 5 min TTL is a good freshness/load balance.
 * - serviceDetail (single service): changes even less often → 15 min TTL, lower DB load.
 * - servicesByCategory: same volatility as catalog → 5 min.
 *
 * Cache invalidation is ALSO done explicitly via @CacheEvict (see VendorServiceManagement),
 * so TTL is the SAFETY NET, not the only mechanism.
 */
@EnableCaching
@Configuration
public class CacheConfig {

    /** Cache name for paginated public catalog listing. */
    public static final String CACHE_SERVICE_CATALOG = "serviceCatalog";
    /** Cache name for a single service lookup by id. */
    public static final String CACHE_SERVICE_DETAIL = "serviceDetail";
    /** Cache name for paginated catalog filtered by category. */
    public static final String CACHE_SERVICES_BY_CATEGORY = "servicesByCategory";

    /** Default TTL applied to any cache name without an explicit override below. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * RedisCacheManager with per-cache TTL overrides (used when Redis is configured).
     *
     * @ConditionalOnProperty: only active when spring.data.redis.host is set, i.e. dev/prod.
     * In the test profile that property is absent, so this bean is skipped and the fallback
     * ConcurrentMapCacheManager below takes over — tests then run with zero Redis dependency.
     *
     * The serializer MUST match what we cache: values are written/read as JSON using the same
     * ObjectMapper configured in RedisConfig (with java.time support + type info). If the cache
     * manager and the value type disagree on serialization, you get ClassCastException on read.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.data.redis.host")
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisJsonSerializer") GenericJackson2JsonRedisSerializer jsonSerializer
    ) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // Prefix every key so our entries don't collide with rate-limit keys or other apps.
                // computePrefixWith sets a converter applied to every cache name; result is
                // "<prefix><cacheName>::<key>". (prefixCacheNamesWith does not exist in this API.)
                .computePrefixWith(cacheName -> "marketplace:cache:" + cacheName + "::")
                .entryTtl(DEFAULT_TTL)
                // Keys as strings, values as JSON.
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new org.springframework.data.redis.serializer.StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // Per-cache TTL: shorter for listings (change more often), longer for single-service detail.
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CACHE_SERVICE_CATALOG, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        perCache.put(CACHE_SERVICE_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(15)));
        perCache.put(CACHE_SERVICES_BY_CATEGORY, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * Fallback CacheManager for when Redis is NOT configured (e.g. the test profile).
     *
     * WHY @ConditionalOnMissingBean(CacheManager.class):
     * - The RedisCacheManager above (a CacheManager) is created when Redis is configured.
     * - When it is absent, Spring would otherwise auto-create its own SimpleCacheManager, but we
     *   declare this explicitly so the cache names we rely on (serviceDetail, etc.) are known to
     *   the test cache and @Cacheable works deterministically in tests.
     * - This manager stores entries in the JVM heap (ConcurrentMap) — fine for tests, NOT for prod.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public org.springframework.cache.concurrent.ConcurrentMapCacheManager fallbackCacheManager() {
        return new org.springframework.cache.concurrent.ConcurrentMapCacheManager(
                CACHE_SERVICE_CATALOG, CACHE_SERVICE_DETAIL, CACHE_SERVICES_BY_CATEGORY);
    }

    /**
     * KeyGenerator for methods whose key argument is a Spring Data Pageable.
     *
     * WHY a custom generator:
     * - Pageable is an interface; its implementations' equals/hashCode are unreliable or absent.
     * - The default key generator calls .hashCode() on the arg → collisions or misses between
     *   "page 0 size 20" and another Pageable that happens to hash the same.
     * - We build a deterministic, human-readable key: "p{page}:s{size}:sort={sort}"
     *   so that the same query params always hit the same cache entry, and you can inspect keys
     *   in redis-cli ("marketplace:cache:serviceCatalog::p0:s20:sort=createdAt: ASC").
     *
     * Registered under bean name "pageableKeyGenerator" so annotations can reference it:
     *   @Cacheable(keyGenerator = "pageableKeyGenerator")
     */
    @Bean("pageableKeyGenerator")
    public KeyGenerator pageableKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder key = new StringBuilder();
            for (Object param : params) {
                if (param instanceof org.springframework.data.domain.Pageable pageable) {
                    key.append("p").append(pageable.getPageNumber())
                       .append(":s").append(pageable.getPageSize());
                    if (pageable.getSort().isSorted()) {
                        key.append(":sort=").append(pageable.getSort().toString().replace(": ", ""));
                    }
                } else {
                    // Fall back to toString for any non-Pageable argument.
                    key.append(param);
                }
            }
            return key.toString();
        };
    }
}
