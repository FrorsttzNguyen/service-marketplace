package com.hien.marketplace.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
     * ObjectMapper used exclusively by Redis serializers (RedisTemplate and RedisCacheManager).
     *
     * WHY copy() from the primary MVC ObjectMapper:
     * - objectMapper.copy() produces an independent instance that inherits all base configuration
     *   (JavaTimeModule, feature flags, etc.) from the primary bean — so cached values serialize
     *   java.time.* types the same way REST responses do, avoiding mismatches on cache read.
     * - The copy is then configured with activateDefaultTyping so Redis can reconstruct concrete
     *   types (e.g. ServiceResponse record) on cache HIT. This is intentional for Redis only.
     * - The PRIMARY MVC ObjectMapper is left untouched — no "@class" ever appears in HTTP responses.
     *
     * WHY NOT @Primary:
     * - This bean must never be used by Spring MVC's HTTP message converter.
     * - JacksonConfig defines the @Primary objectMapper for MVC; this one is only reached via
     *   @Qualifier("redisObjectMapper") in RedisTemplate and RedisCacheManager.
     *
     * WHY setVisibility(ALL, ANY):
     * - Java records expose fields directly (no getters), so Jackson's default accessor scan
     *   misses them. Setting visibility to ANY ensures fields are serialized even without getters.
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper(ObjectMapper objectMapper) {
        // Copy inherits base config (modules, features) without mutating the shared MVC mapper.
        ObjectMapper redisCopy = objectMapper.copy();
        // Serialize fields directly (needed for Java records which have no traditional getters).
        redisCopy.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // Embed "@class": "<fqcn>" so deserialization knows the concrete type.
        // LaissezFaireSubTypeValidator is the validator Jackson requires when default typing is on;
        // it allows every subtype (acceptable for an internal cache, NOT for untrusted input).
        redisCopy.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );
        return redisCopy;
    }

    /**
     * Primary RedisTemplate for string-keyed operations.
     *
     * ConnectionFactory is auto-configured by Spring Boot from spring.data.redis.* properties.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

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
