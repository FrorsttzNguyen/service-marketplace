package com.hien.marketplace.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.interfaces.dto.response.ErrorResponse;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for RateLimitFilter.
 *
 * WHY a standalone filter test (not @SpringBootTest):
 * - The full app context disables rate limiting in the test profile (app.ratelimit.enabled=false)
 *   so existing auth tests are not blocked. We therefore test the filter in isolation by
 *   constructing it directly with enabled=true, exercising the in-memory bucket path.
 * - No Redis dependency: we pass an ObjectProvider that returns null for the ProxyManager, so the
 *   filter uses its ConcurrentHashMap fallback. That path is identical in algorithm to the Redis
 *   path (token bucket), just in-JVM — which is exactly what we want to assert behaviorally.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        ObjectProvider<LettuceBasedProxyManager<byte[]>> emptyProvider = mock(ObjectProvider.class);
        // getIfAvailable() returns null → filter falls back to in-memory buckets.
        org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);

        // No trusted proxies → X-Forwarded-For is ignored, identity is the socket IP.
        filter = new RateLimitFilter(emptyProvider, new ObjectMapper(), true, "");
        chain = mock(FilterChain.class);
    }

    /** Build a filter that trusts a given proxy CIDR (so it honors X-Forwarded-For from it). */
    private RateLimitFilter filterTrusting(String trustedProxiesCsv) {
        ObjectProvider<LettuceBasedProxyManager<byte[]>> emptyProvider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
        return new RateLimitFilter(emptyProvider, new ObjectMapper(), true, trustedProxiesCsv);
    }

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr("1.2.3.4");
        return req;
    }

    @Test
    @DisplayName("Login: 5 requests allowed, 6th returns 429")
    void loginAllowsFiveThenBlocks() throws Exception {
        for (int i = 1; i <= 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/auth/login"), res, chain);
            assertThat(res.getStatus()).as("request %d should pass", i).isEqualTo(200);
        }
        verify(chain, times(5)).doFilter(any(), any());

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/auth/login"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        // The 6th request must NOT proceed down the chain.
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Register: only 3 per hour allowed")
    void registerAllowsThreeThenBlocks() throws Exception {
        for (int i = 1; i <= 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(post("/api/auth/register"), res, chain);
            assertThat(res.getStatus()).as("request %d should pass", i).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/auth/register"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Different IPs have independent buckets")
    void differentIpSeparateBuckets() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = post("/api/auth/login");
            req.setRemoteAddr("9.9.9.9");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        // A different IP is a fresh bucket and should not be affected.
        MockHttpServletRequest otherIp = post("/api/auth/login");
        otherIp.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(otherIp, res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Non-auth endpoints are never rate-limited")
    void nonAuthEndpointsPassThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/services");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("When disabled, the filter lets everything through")
    void disabledLetsEverythingThrough() throws Exception {
        ObjectProvider<LettuceBasedProxyManager<byte[]>> emptyProvider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
        RateLimitFilter disabledFilter = new RateLimitFilter(emptyProvider, new ObjectMapper(), false, "");

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            disabledFilter.doFilter(post("/api/auth/login"), res, chain);
            assertThat(res.getStatus()).as("request %d", i).isEqualTo(200);
        }
        verify(chain, times(20)).doFilter(any(), any());
    }

    @Test
    @DisplayName("429 body is a valid ErrorResponse JSON")
    void tooManyRequestsBodyIsValidErrorJson() throws Exception {
        for (int i = 0; i < 5; i++) {
            filter.doFilter(post("/api/auth/login"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/auth/login"), blocked, chain);

        ObjectMapper mapper = new ObjectMapper();
        ErrorResponse body = mapper.readValue(blocked.getContentAsString(), ErrorResponse.class);
        assertThat(body.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(body.message()).contains("Too many requests");
    }

    @Test
    @DisplayName("Untrusted peer: X-Forwarded-For is IGNORED → cannot bypass by spoofing it")
    void xForwardedForIgnoredWhenProxyUntrusted() throws Exception {
        // remoteAddr is 1.2.3.4 (post() helper), which is NOT a trusted proxy in the default filter.
        // Exhaust the bucket for that socket IP using one spoofed XFF...
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = post("/api/auth/login");
            req.addHeader("X-Forwarded-For", "203.0.113.7");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }
        // ...then a request with a DIFFERENT spoofed XFF but the same socket IP must STILL be blocked,
        // proving XFF did not create a fresh bucket (no bypass).
        MockHttpServletRequest spoofed = post("/api/auth/login");
        spoofed.addHeader("X-Forwarded-For", "9.9.9.9");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(spoofed, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Trusted proxy: X-Forwarded-For is honored as the client identity")
    void xForwardedForHonoredWhenProxyTrusted() throws Exception {
        // The socket peer 1.2.3.4 (post() helper) is declared trusted, so XFF is used as identity.
        RateLimitFilter trusting = filterTrusting("1.2.3.4");

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = post("/api/auth/login");
            req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
            trusting.doFilter(req, new MockHttpServletResponse(), chain);
        }
        // Same XFF client → blocked.
        MockHttpServletRequest req = post("/api/auth/login");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        trusting.doFilter(req, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        // Different XFF client (same trusted proxy) → separate bucket.
        MockHttpServletRequest otherReq = post("/api/auth/login");
        otherReq.addHeader("X-Forwarded-For", "198.51.100.2");
        MockHttpServletResponse otherRes = new MockHttpServletResponse();
        trusting.doFilter(otherReq, otherRes, chain);
        assertThat(otherRes.getStatus()).isEqualTo(200);
    }
}
