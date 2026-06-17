package com.hien.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the MVC ObjectMapper and the Redis ObjectMapper are properly isolated:
 * - The MVC (primary) ObjectMapper must NOT have default typing enabled.
 * - The Redis ObjectMapper must have default typing enabled (for cache round-trip).
 * - They must be different instances (the copy() ensures full independence).
 *
 * WHY this test matters:
 * The bug this guards against: when RedisConfig creates an ObjectMapper bean with
 * activateDefaultTyping, Spring Boot's @ConditionalOnMissingBean skips creating its
 * own clean MVC ObjectMapper, causing "@class" type metadata to leak into REST responses.
 * JacksonConfig fixes this by declaring an explicit @Primary clean ObjectMapper.
 */
@DisplayName("ObjectMapper isolation: MVC vs Redis")
class JacksonObjectMapperIsolationTest {

    /**
     * Simulates what Spring does at startup: JacksonConfig provides the @Primary MVC mapper,
     * then RedisConfig derives the Redis mapper from a copy of it.
     */
    private ObjectMapper buildMvcMapper() {
        // Jackson2ObjectMapperBuilder with defaults — same as what JacksonConfig.objectMapper() does.
        return new Jackson2ObjectMapperBuilder().build();
    }

    private ObjectMapper buildRedisMapper(ObjectMapper mvcMapper) {
        // Same logic as RedisConfig.redisObjectMapper().
        JacksonConfig jacksonConfig = new JacksonConfig();
        RedisConfig redisConfig = new RedisConfig();
        // redisObjectMapper takes the primary MVC mapper and copies it.
        return redisConfig.redisObjectMapper(mvcMapper);
    }

    @Test
    @DisplayName("Primary MVC ObjectMapper must not have default typing (no @class in responses)")
    void mvcObjectMapperHasNoDefaultTyping() throws Exception {
        ObjectMapper mvcMapper = buildMvcMapper();

        // Serialize a simple non-final object — no @class should appear.
        String json = mvcMapper.writeValueAsString(new SampleNonFinal("hello", 42));

        assertThat(json)
                .as("MVC ObjectMapper must not emit @class type metadata")
                .doesNotContain("@class");
    }

    @Test
    @DisplayName("Redis ObjectMapper must have default typing (@class for cache round-trip)")
    void redisObjectMapperHasDefaultTyping() throws Exception {
        ObjectMapper mvcMapper = buildMvcMapper();
        ObjectMapper redisMapper = buildRedisMapper(mvcMapper);

        String json = redisMapper.writeValueAsString(new SampleNonFinal("hello", 42));

        assertThat(json)
                .as("Redis ObjectMapper must emit @class for concrete type reconstruction on cache HIT")
                .contains("@class");
    }

    @Test
    @DisplayName("MVC and Redis ObjectMapper must be different instances (copy() not same ref)")
    void mvcAndRedisObjectMapperAreDifferentInstances() {
        ObjectMapper mvcMapper = buildMvcMapper();
        ObjectMapper redisMapper = buildRedisMapper(mvcMapper);

        assertThat(redisMapper)
                .as("Redis ObjectMapper must be a separate instance from the MVC mapper")
                .isNotSameAs(mvcMapper);
    }

    @Test
    @DisplayName("Adding typing to Redis copy must not pollute the MVC ObjectMapper")
    void activatingTypingOnRedisCopyDoesNotMutateMvcMapper() throws Exception {
        ObjectMapper mvcMapper = buildMvcMapper();
        // Build redis copy (which activates default typing on the copy).
        buildRedisMapper(mvcMapper);

        // After the copy was created and typed, the original MVC mapper must still be clean.
        String json = mvcMapper.writeValueAsString(new SampleNonFinal("hello", 42));
        assertThat(json)
                .as("Activating typing on the Redis copy must NOT mutate the primary MVC ObjectMapper")
                .doesNotContain("@class");
    }

    /** Simple non-final POJO used to trigger NON_FINAL default typing. */
    static class SampleNonFinal {
        public final String name;
        public final int value;

        SampleNonFinal(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
