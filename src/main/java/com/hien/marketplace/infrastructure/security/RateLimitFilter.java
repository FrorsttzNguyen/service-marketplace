package com.hien.marketplace.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.interfaces.dto.response.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.local.LocalBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limiting filter for authentication endpoints (Phase 5).
 *
 * WHAT IT DOES:
 * - Intercepts POST /api/auth/{login,register,refresh}.
 * - For each (endpoint, client IP) pair, maintains a token bucket.
 * - Allows up to the configured number of requests per period; once exhausted, returns
 *   429 Too Many Requests with a Retry-After header and a JSON ErrorResponse body.
 *
 * WHY A FILTER (not @PreAuthorize / interceptor):
 * - Rate limiting must happen BEFORE the request reaches the controller — a brute-force attempt
 *   should never cost a bcrypt hash or a DB query.
 * - Filters run at the very front of the Spring MVC chain, before handler mapping.
 *
 * TOKEN BUCKET (the algorithm):
 * - Each bucket has a Bandwidth(capacity, refillPeriod): it starts full and refills `capacity`
 *   tokens every `refillPeriod`, never exceeding capacity.
 * - Every request calls tryConsume(1): if >= 1 token available, consume it and proceed.
 *   If 0 tokens, reject with 429.
 * - This models a "burst then steady" allowance: a legit user can make a few quick requests, but a
 *   sustained attacker is capped at capacity/period.
 *
 * KEY = "ratelimit:<endpoint>:<ip>". The IP is the rate-limit identity.
 *
 * IP EXTRACTION — security consideration:
 * - We prefer the X-Forwarded-For header (set by reverse proxies / load balancers) because the
 *   servlet remoteAddr is the proxy's IP, not the real client's.
 * - X-Forwarded-For can be SPOOFED by the client if the proxy does not strip/overwrite it. In
 *   production behind a trusted proxy, configure that proxy to set XFF authoritatively. For this
 *   learning project we take the first XFF value, which is the convention.
 *
 * FALLBACK (in-memory) when no Redis ProxyManager is present:
 * - In tests (and any profile without spring.data.redis.host), the ProxyManager bean is absent.
 *   We then keep a local ConcurrentHashMap<String, Bucket> so rate-limit behavior still works.
 *   This is intentionally per-instance and exists for testability, NOT for production correctness.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Endpoint → bucket configuration. Loaded in the constructor. */
    private final Map<String, BucketConfiguration> endpointConfigs;

    /** Distributed Redis-backed proxy manager; absent in test profile. */
    private final ObjectProvider<LettuceBasedProxyManager<byte[]>> proxyManagerProvider;

    /** In-memory fallback buckets, keyed by "endpoint:ip". Only used when no ProxyManager. */
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    /**
     * Master on/off switch. WHY: existing integration tests (auth/booking) legitimately hammer
     * /api/auth/** within one shared ApplicationContext, which would trip the rate limiter and turn
     * those tests into false 429s. We keep rate limiting ON by default (app.ratelimit.enabled=true)
     * and disable it only via application-test.yml, so the dedicated RateLimitFilterTest (Task 6)
     * can still assert the real throttling behavior on an isolated filter instance.
     */
    private final boolean enabled;

    /**
     * Per-endpoint limits.
     *
     * WHY these numbers:
     * - login: 5 / minute — generous for a forgetful human, harsh for a credential stuffer.
     *   (A real attacker rotates IPs anyway; this raises the bar and stops naive scripts.)
     * - register: 3 / hour — signup spam/bot accounts are expensive (DB writes, email sends).
     * - refresh: 10 / minute — clients refresh proactively; allow headroom.
     */
    public RateLimitFilter(ObjectProvider<LettuceBasedProxyManager<byte[]>> proxyManagerProvider,
                           ObjectMapper objectMapper, boolean enabled) {
        this.proxyManagerProvider = proxyManagerProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;

        Bandwidth loginLimit = Bandwidth.builder()
                .capacity(5).refillIntervally(5, Duration.ofMinutes(1)).build();
        Bandwidth registerLimit = Bandwidth.builder()
                .capacity(3).refillIntervally(3, Duration.ofHours(1)).build();
        Bandwidth refreshLimit = Bandwidth.builder()
                .capacity(10).refillIntervally(10, Duration.ofMinutes(1)).build();

        endpointConfigs = Map.of(
                "/api/auth/login", BucketConfiguration.builder().addLimit(loginLimit).build(),
                "/api/auth/register", BucketConfiguration.builder().addLimit(registerLimit).build(),
                "/api/auth/refresh", BucketConfiguration.builder().addLimit(refreshLimit).build()
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Bypass entirely when disabled (test profile) — see `enabled` field javadoc.
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        BucketConfiguration config = endpointConfigs.get(path);

        // Only rate-limit the specific auth POST endpoints. Everything else passes through.
        if (config == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String bucketKey = path + ":" + clientIp;

        ConsumptionProbe probe = consume(bucketKey, config);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequests(response, probe.getNanosToWaitForRefill());
        }
    }

    /**
     * Attempt to consume one token from the bucket for (path, ip).
     *
     * Uses the Redis ProxyManager if available (distributed), else the in-memory fallback.
     */
    private ConsumptionProbe consume(String bucketKey, BucketConfiguration config) {
        LettuceBasedProxyManager<byte[]> proxyManager = proxyManagerProvider.getIfAvailable();
        if (proxyManager != null) {
            byte[] redisKey = bucketKey.getBytes(StandardCharsets.UTF_8);
            // Bucket4j 8.10 API: proxyManager.builder().build(key, configuration).
            // build(key, configuration) reuses the stored state if the bucket already exists, or
            // initializes it with `configuration` on first access.
            BucketProxy proxy = proxyManager.builder().build(redisKey, config);
            return proxy.tryConsumeAndReturnRemaining(1);
        }
        // In-memory fallback: lazily create a local Bucket from the same Bandwidths.
        // Bucket.builder().addLimit(Bandwidth) is the local-bucket API; we unwrap the config's
        // bandwidths to avoid maintaining a parallel Bandwidth map.
        Bucket bucket = localBuckets.computeIfAbsent(bucketKey, k -> {
            LocalBucketBuilder builder = Bucket.builder();
            for (Bandwidth bandwidth : config.getBandwidths()) {
                builder.addLimit(bandwidth);
            }
            return builder.build();
        });
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    /**
     * Resolve the client IP, preferring X-Forwarded-For (set by proxies).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF is "client, proxy1, proxy2"; the first entry is the originating client.
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Write a 429 response consistent with the rest of the API (ErrorResponse JSON).
     *
     * Retry-After is the standard header telling clients how many seconds to wait. We round up
     * the bucket's nanosToWaitForRefill to whole seconds (minimum 1).
     */
    private void writeTooManyRequests(HttpServletResponse response, long nanosToWaitForRefill) throws IOException {
        long retryAfterSeconds = Math.max(1, (nanosToWaitForRefill + 999_999_999L) / 1_000_000_000L);

        log.warn("Rate limit exceeded; retry-after {}s", retryAfterSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse body = ErrorResponse.of(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry after " + retryAfterSeconds + "s."
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
