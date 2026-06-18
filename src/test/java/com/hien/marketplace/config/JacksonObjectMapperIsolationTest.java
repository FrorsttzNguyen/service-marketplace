package com.hien.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the isolation between the MVC ObjectMapper and Redis cache serialization:
 * - The MVC (primary) ObjectMapper must NOT emit "@class" type metadata in REST responses.
 * - The Redis cache serializer MUST emit "@class" so it can reconstruct concrete types on cache HIT.
 * - The two concerns never share a mapper, so configuring the cache serializer cannot pollute MVC.
 *
 * NOTE on the architecture (post-fix): Redis cache serialization no longer exposes a separate
 * ObjectMapper bean. GenericJackson2JsonRedisSerializer (RedisConfig#cacheJsonSerializer) owns its
 * own internal mapper where default typing lives, so the "@class"-leak-into-REST risk is structurally
 * impossible. The deep round-trip behavior of that serializer is covered by RedisCacheSerializationTest.
 */
@DisplayName("ObjectMapper isolation: MVC vs Redis cache")
class JacksonObjectMapperIsolationTest {

    /** Jackson2ObjectMapperBuilder with defaults — same as JacksonConfig.objectMapper(). */
    private ObjectMapper buildMvcMapper() {
        return new Jackson2ObjectMapperBuilder().build();
    }

    @Test
    @DisplayName("Primary MVC ObjectMapper must not emit @class (no type metadata in responses)")
    void mvcObjectMapperHasNoDefaultTyping() throws Exception {
        String json = buildMvcMapper().writeValueAsString(new SampleNonFinal("hello", 42));

        assertThat(json)
                .as("MVC ObjectMapper must not emit @class type metadata")
                .doesNotContain("@class");
    }

    @Test
    @DisplayName("Redis cache serializer must emit @class for concrete-type reconstruction on cache HIT")
    void cacheSerializerEmitsClassTypeInfo() {
        GenericJackson2JsonRedisSerializer serializer = RedisConfig.cacheJsonSerializer();

        String json = new String(serializer.serialize(new SampleNonFinal("hello", 42)));

        assertThat(json)
                .as("cache serializer must embed @class so deserialize() can rebuild the concrete type")
                .contains("@class");
    }

    @Test
    @DisplayName("Building the cache serializer must not enable typing on a fresh MVC mapper")
    void cacheSerializerDoesNotPolluteMvcMapper() throws Exception {
        ObjectMapper mvcMapper = buildMvcMapper();
        // Build the cache serializer (which configures default typing on its OWN internal mapper).
        RedisConfig.cacheJsonSerializer();

        // The independent MVC mapper must remain clean.
        String json = mvcMapper.writeValueAsString(new SampleNonFinal("hello", 42));
        assertThat(json)
                .as("the cache serializer owns a separate mapper; the MVC mapper must stay clean")
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
