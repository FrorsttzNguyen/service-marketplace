package com.hien.marketplace.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for caching and rate limiting (Phase 5).
 *
 * WHY a custom RedisTemplate instead of the default:
 * - Spring Boot's auto-configured RedisTemplate uses JdkSerializationRedisSerializer by default.
 * - JDK serialization stores values as opaque binary blobs with package-qualified class names.
 *   Problems with that:
 *     1. Not human-readable → hard to debug in redis-cli (you see garbled bytes).
 *     2. Brittle: renaming/refactoring a cached class breaks deserialization of old entries.
 *     3. Not portable across languages (a future Node/Go service can't read it).
 * - We override BOTH key and value serializers:
 *     - Keys: StringRedisSerializer (cache keys are always strings like "marketplace:cache:serviceCatalog::1").
 *     - Values: GenericJackson2JsonRedisSerializer (stores JSON + a "@class" type hint so Spring can
 *       reconstruct the exact type on read — this is what makes @Cacheable work with records/POJOs).
 *
 * WHY ObjectMapper customization:
 * - JavaTimeModule: so java.time types (LocalDateTime, LocalDate, BigDecimal amounts) serialize as
 *   ISO strings instead of throwing "Java 8 date/time not supported".
 * - activateDefaultTyping: embeds the concrete class name so a cache storing a ServiceResponse record
 *   can be read back AS a ServiceResponse, not as a LinkedHashMap.
 *
 * NOTE: This template is the LOW-LEVEL building block. For @Cacheable we use a RedisCacheManager
 * configured in CacheConfig (which also uses JSON serialization). This RedisTemplate is mainly for
 * manual access — e.g. rate-limit bucket storage, debugging, or future features needing raw Redis ops.
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConfig {

    /**
     * Build the JSON serializer used for BOTH the RedisTemplate values and the @Cacheable cache
     * (see CacheConfig). Shared as a static factory so the regression test can exercise the EXACT
     * construction the app uses (RedisCacheSerializationTest), guarding against config drift.
     *
     * WHY no-arg constructor + configure() (this is the bug fix):
     * - The no-arg GenericJackson2JsonRedisSerializer sets up its OWN default typing, which writes a
     *   top-level "@class" on every cached object AND reads it back symmetrically.
     * - The previous code instead passed a hand-built ObjectMapper with
     *   activateDefaultTyping(..., As.PROPERTY). That produced ASYMMETRIC output: nested non-final
     *   values got a ["fqcn", value] wrapper but the ROOT object had NO "@class". On a cache HIT the
     *   read side (which reads into Object with default typing on) then failed with
     *   "missing type id property '@class'" → every cache hit returned HTTP 500. It was latent
     *   because the in-memory test cache doesn't serialize, and prod cache hits only happen within
     *   the TTL window.
     * - configure() only ADDS what the no-arg mapper lacks: JavaTimeModule (for LocalDateTime in the
     *   cached DTOs) and field visibility (Java records expose fields, not getters). It does NOT
     *   touch the typing setup, so the symmetric "@class" handling is preserved.
     */
    public static GenericJackson2JsonRedisSerializer cacheJsonSerializer() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        serializer.configure(mapper -> {
            mapper.registerModule(new JavaTimeModule());
            mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        });
        return serializer;
    }

    /**
     * Shared JSON serializer bean for Redis values (RedisTemplate + RedisCacheManager).
     * Conditional via the class-level @ConditionalOnProperty, so it only exists when Redis is wired.
     */
    @Bean("redisJsonSerializer")
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        return cacheJsonSerializer();
    }

    /**
     * Primary RedisTemplate for string-keyed operations.
     *
     * ConnectionFactory is auto-configured by Spring Boot from spring.data.redis.* properties.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisJsonSerializer") GenericJackson2JsonRedisSerializer jsonSerializer
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Keys are always strings (cache names, rate-limit keys).
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // Values are JSON objects.
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
