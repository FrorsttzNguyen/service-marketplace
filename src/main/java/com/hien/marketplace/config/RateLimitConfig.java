package com.hien.marketplace.config;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rate-limiting infrastructure backed by Redis (Phase 5).
 *
 * WHY distributed (Redis) rate limiting instead of in-memory:
 * - In-memory rate limiting (a Map<ip, bucket> in the JVM) is per-instance. Behind a load balancer
 *   with N instances, an attacker effectively gets N * limit requests — the protection is divided.
 * - Redis is shared state: every instance reads/writes the same bucket for a given key, so the
 *   configured limit holds globally regardless of how many instances exist.
 *
 * HOW BUCKET4J + REDIS WORK TOGETHER:
 * - Bucket4j defines the token-bucket algorithm (capacity, refill rate) and a BucketProxy.
 * - The bucket STATE (tokens remaining, last refill time) is NOT stored in the JVM — it is stored
 *   in Redis under a key, via a ProxyManager. Each tryConsume is a Redis read-modify-write.
 * - ProxyManager<K>: maps a key (e.g. "ratelimit:login:1.2.3.4") to a bucket.
 *
 * WHY @ConditionalOnProperty("spring.data.redis.host"):
 * - In the TEST profile we run without a real Redis (cache type = simple, no Redis needed).
 * - This config bean is only created when a Redis host is configured, so tests don't need a
 *   ProxyManager bean and won't fail context loading. The RateLimitFilter uses an in-memory
 *   ConcurrentHashMap fallback when no ProxyManager is present, so rate-limit behavior is still
 *   testable without Redis.
 *
 * WHY a dedicated RedisClient:
 * - Bucket4j's Lettuce integration wants a raw RedisClient to run its compare-and-swap scripts.
 * - We create a separate client so rate-limit script traffic does not contend with cache reads.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RateLimitConfig {

    /**
     * Dedicated RedisClient for rate-limit bucket storage.
     *
     * destroyMethod = "shutdown" ensures the Lettuce client's event loop is closed on app stop.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password
    ) {
        String uri = (password == null || password.isBlank())
                ? "redis://" + host + ":" + port
                : "redis://:" + password + "@" + host + ":" + port;
        return RedisClient.create(uri);
    }

    /**
     * ProxyManager that stores bucket state in Redis keyed by byte[] (default Lettuce key codec).
     *
     * Key serialization is handled by the RateLimitFilter, which builds a String key and the filter
     * is the only consumer of this ProxyManager.
     */
    @Bean
    public LettuceBasedProxyManager<byte[]> rateLimitProxyManager(RedisClient rateLimitRedisClient) {
        // builderFor(RedisClient) returns a builder whose key type is byte[] (the default codec).
        return LettuceBasedProxyManager.builderFor(rateLimitRedisClient).build();
    }
}
