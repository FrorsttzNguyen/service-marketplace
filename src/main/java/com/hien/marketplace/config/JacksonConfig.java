package com.hien.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Explicit primary ObjectMapper for Spring MVC HTTP message conversion.
 *
 * WHY this class exists:
 * When spring.data.redis.host is configured, RedisConfig creates a "redisObjectMapper" bean
 * of type ObjectMapper (needed for Redis cache serialization with default typing).
 * Spring Boot's JacksonAutoConfiguration uses @ConditionalOnMissingBean on its ObjectMapper
 * factory method, so when it sees ANY ObjectMapper bean already exists, it skips creating
 * the clean auto-configured one. Spring MVC then falls back to using the Redis mapper —
 * which has activateDefaultTyping enabled — and "@class" type metadata leaks into every
 * HTTP response.
 *
 * The fix: declare an explicit @Primary ObjectMapper here (always active, no @ConditionalOnProperty)
 * so Spring MVC always has a clean, well-configured mapper regardless of Redis being present.
 * RedisConfig then builds its redis-specific mapper from a copy of this one.
 *
 * WHY Jackson2ObjectMapperBuilder (not new ObjectMapper()):
 * - Spring Boot's builder applies all auto-configuration (java.time support, feature flags,
 *   @JsonComponent beans, property naming strategy, etc.) consistently.
 * - If we just did "new ObjectMapper()", we'd lose features like WRITE_DATES_AS_TIMESTAMPS=false.
 * - The builder is the canonical way to obtain a properly-configured MVC ObjectMapper.
 */
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper used by Spring MVC for all HTTP REST responses.
     *
     * - NO default typing: REST responses must never contain "@class" metadata.
     * - Built via Jackson2ObjectMapperBuilder so all Spring Boot auto-configuration
     *   (JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS=false, etc.) is applied.
     * - @Primary: Spring MVC's MappingJackson2HttpMessageConverter will use this bean
     *   when choosing among multiple ObjectMapper beans.
     * - This bean also satisfies @ConditionalOnMissingBean in JacksonAutoConfiguration,
     *   so Spring Boot will not try to create a duplicate.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
