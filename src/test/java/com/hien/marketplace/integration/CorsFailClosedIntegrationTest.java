package com.hien.marketplace.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fail-closed CORS test: when {@code app.cors.allowed-origins} is EMPTY (the default in
 * application.yml), NO cross-origin browser request is allowed — not even localhost.
 *
 * WHY a separate top-level class (not @Nested inside CorsIntegrationTest):
 * - This scenario needs a DIFFERENT Spring context (empty allow-list vs the populated one in
 *   CorsIntegrationTest). Spring Test does not reliably support @Nested classes that declare their
 *   own @SpringBootTest with different properties — the nested context can fail to inherit the
 *   enclosing class's @ActiveProfiles("test"), which then breaks H2/datasource wiring. A separate
 *   top-level class with its own full annotation set is the conventional, reliable pattern.
 *
 * Security meaning: a fresh deploy that forgets to set APP_CORS_ALLOWED_ORIGINS does NOT silently
 * open the API to the world — the browser app breaks loudly instead. That is the intended default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=")
class CorsFailClosedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("empty allow-list → localhost preflight is rejected (403, no ACAO header)")
    void preflight_whenAllowListEmpty_rejected() throws Exception {
        // When the allow-list is empty (the default), Spring treats every Origin as invalid and the
        // CORS processor rejects the request with 403 rather than silently allowing it. That is the
        // fail-closed guarantee: a misconfigured deploy breaks the browser app loudly, it does NOT
        // open the API to the world.
        mockMvc.perform(options("/api/services")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
