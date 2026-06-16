package com.hien.marketplace.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
     * ObjectMapper shared by both the RedisTemplate value serializer and the RedisCacheManager.
     *
     * WHY a dedicated bean name "redisObjectMapper":
     * - We do NOT want to override the primary ObjectMapper that Spring uses for REST JSON responses.
     * - REST responses should NOT include "@class" type hints (security + size).
     * - Cache values SHOULD include them (needed to reconstruct exact types on read).
     * - A separate bean keeps these concerns isolated.
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Support java.time.* and other Java 8 types in cached values.
        mapper.registerModule(new JavaTimeModule());
        // Serialize fields directly (ignore getters/setters mismatch on records).
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // Embed "@class": "<fqcn>" so deserialization knows the concrete type.
        // LaissezFaireSubTypeValidator is the validator Jackson requires when default typing is on;
        // it allows every subtype (acceptable for an internal cache, NOT for untrusted input).
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );
        return mapper;
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
