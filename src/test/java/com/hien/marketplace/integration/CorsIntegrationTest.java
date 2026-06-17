package com.hien.marketplace.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Phase 7 CORS allow-list (SecurityConfig#corsConfigurationSource).
 *
 * WHY a dedicated test class:
 * - CORS behavior is configured by the `app.cors.allowed-origins` property. To test the "allowed"
 *   and "blocked" cases deterministically we must override that property per test, which is why
 *   these tests live in their own class with a fixed @TestPropertySource instead of being bolted
 *   onto ServiceControllerIntegrationTest.
 *
 * What we verify (the two things a browser actually checks):
 *  1. Preflight: an OPTIONS request with Origin + Access-Control-Request-Method must return
 *     2xx + Access-Control-Allow-Origin echoing the (allowed) origin.
 *  2. Actual request: a GET with Origin must return the response with Access-Control-Allow-Origin.
 *  3. Blocked origin: no Access-Control-Allow-Origin header at all (browser will reject).
 *
 * Test profile notes:
 * - /api/services is PUBLIC (see SecurityConfig), so these tests don't need a JWT — they isolate
 *   CORS from auth. The rate-limit filter is disabled in the test profile, so repeated OPTIONS/GET
 *   won't be throttled either.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Two allowed origins: exact dev origin + a wildcard Vercel pattern.
        "app.cors.allowed-origins=http://localhost:3000,https://*.vercel.app"
})
class CorsIntegrationTest {

    private static final String ALLOWED_LOCAL = "http://localhost:3000";
    private static final String ALLOWED_VERCEL = "https://service-marketplace-abc123.vercel.app";
    private static final String BLOCKED_ORIGIN = "https://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Preflight (OPTIONS) — the browser's first check before a cross-origin call")
    class Preflight {

        @Test
        @DisplayName("allowed local origin → 2xx + ACAO echoes origin + required preflight headers")
        void preflight_allowedLocalOrigin_echoesBack() throws Exception {
            mockMvc.perform(options("/api/services")
                            .header("Origin", ALLOWED_LOCAL)
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    // Echoes the exact origin (never "*") because allowCredentials=true.
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_LOCAL))
                    .andExpect(header().exists("Access-Control-Allow-Methods"))
                    .andExpect(header().exists("Access-Control-Allow-Headers"))
                    // 1h preflight cache — cuts preflight round-trips on repeat calls.
                    .andExpect(header().longValue("Access-Control-Max-Age", 3600L));
        }

        @Test
        @DisplayName("allowed Vercel preview origin matches the https://*.vercel.app pattern")
        void preflight_allowedVercelOrigin_matchesWildcardPattern() throws Exception {
            mockMvc.perform(options("/api/services")
                            .header("Origin", ALLOWED_VERCEL)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_VERCEL));
        }

        @Test
        @DisplayName("blocked origin → 403 (Spring rejects the request outright)")
        void preflight_blockedOrigin_rejected() throws Exception {
            // Spring's CORS processor REJECTS a preflight whose Origin isn't in the allow-list by
            // short-circuiting to 403 — it does not return 200-without-headers. Either way the
            // browser blocks the call; we assert the actual Spring behavior (403 + no ACAO header).
            mockMvc.perform(options("/api/services")
                            .header("Origin", BLOCKED_ORIGIN)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("Actual request — the real GET the browser sends after a successful preflight")
    class ActualRequest {

        @Test
        @DisplayName("allowed origin → ACAO header present on the 200 response")
        void get_allowedOrigin_hasAllowHeader() throws Exception {
            mockMvc.perform(get("/api/services").header("Origin", ALLOWED_LOCAL))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_LOCAL));
        }

        @Test
        @DisplayName("blocked origin → 403 (path is public but CORS still rejects the cross-origin call)")
        void get_blockedOrigin_rejected() throws Exception {
            mockMvc.perform(get("/api/services").header("Origin", BLOCKED_ORIGIN))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }
}
