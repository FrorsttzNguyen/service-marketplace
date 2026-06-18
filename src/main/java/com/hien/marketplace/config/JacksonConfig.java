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
 * Spring MVC's HTTP responses must use a CLEAN mapper with no default typing — "@class" metadata
 * must never leak into REST payloads. Declaring an explicit @Primary ObjectMapper here guarantees
 * MVC always has a clean, well-configured mapper and pins it as primary if any other ObjectMapper
 * bean ever appears.
 *
 * NOTE: Redis cache serialization does NOT use this bean. GenericJackson2JsonRedisSerializer owns
 * its own internal mapper (see RedisConfig#cacheJsonSerializer) where default typing lives, so the
 * "@class"-leak risk is structurally impossible — the two concerns never share a mapper.
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
