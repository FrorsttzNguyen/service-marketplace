package com.hien.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.infrastructure.security.RateLimitFilter;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for JWT-based authentication.
 *
 * WHY: Configure security rules for API endpoints.
 * - Define which endpoints require authentication
 * - Configure JWT filter to validate tokens
 * - Set password encoder for user passwords
 *
 * SECURITY RULES:
 * - PUBLIC: /api/auth/**, /api/services/**, /api/webhooks/stripe, Swagger, health
 * - VENDOR role: /api/vendor/** (vendor management endpoints)
 * - ADMIN role: /api/admin/** (admin endpoints)
 * - AUTHENTICATED: All other /api/** endpoints
 *
 * ROLE CHECKS:
 * - URL-level role guards in SecurityConfig (primary)
 * - Service-layer ownership checks (secondary, for business logic)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * RateLimitFilter bean (Phase 5).
     *
     * WHY an ObjectProvider for the ProxyManager:
     * - The LettuceBasedProxyManager bean is @ConditionalOnProperty(spring.data.redis.host), so it
     *   is ABSENT in the test profile (no Redis). ObjectProvider lets the filter start even when the
     *   bean is missing, and fall back to an in-memory bucket map (see RateLimitFilter).
     */
    @Bean
    public RateLimitFilter rateLimitFilter(
            ObjectProvider<LettuceBasedProxyManager<byte[]>> proxyManagerProvider,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${app.ratelimit.enabled:true}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${app.ratelimit.trusted-proxies:}") String trustedProxies
    ) {
        return new RateLimitFilter(proxyManagerProvider, objectMapper, enabled, trustedProxies);
    }

    /**
     * Main security filter chain configuration.
     *
     * WHY: Defines the security rules for all HTTP requests.
     *
     * SessionCreationPolicy.STATELESS:
     * - No HTTP session created (JWT is stateless)
     * - Each request must include JWT token
     * - Server doesn't store user session
     *
     * rateLimitFilter is injected as a method param (Spring resolves the bean declared above).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitFilter rateLimitFilter,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                // Enable CORS using the corsConfigurationSource bean below.
                // WHY: the Phase 7 frontend (Next.js on Vercel, dev on http://localhost:3000) is a
                // different origin than the API, so the browser will block cross-origin XHR/fetch
                // unless the API sends the right Access-Control-Allow-* headers. Spring Security's
                // .cors() hook delegates to CorsConfigurationSource and also auto-handles the
                // OPTIONS preflight (a browser sends a preflight before the real request when the
                // call uses Authorization headers, JSON content-type, etc.).
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Disable CSRF - not needed for stateless JWT API
                .csrf(AbstractHttpConfigurer::disable)

                // Configure session management - STATELESS for JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure endpoint authorization rules
                .authorizeHttpRequests(auth ->
                        auth
                                // /api/auth/me is the ONE authenticated auth path — it identifies the
                                // caller from their JWT. It must come BEFORE the "/api/auth/**" permitAll
                                // below (first match wins), or it would be treated as public.
                                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/me").authenticated()
                                // Public endpoints - no authentication required
                                .requestMatchers(
                                        "/api/auth/**",  // Register, login, refresh
                                        "/api/services/**",  // Public service catalog
                                        "/api/reviews/service/**",  // Public reviews
                                        "/api/reviews/vendor/**",  // Public vendor reviews
                                        "/api/webhooks/stripe",  // Stripe webhook (PUBLIC - security via signature)
                                        "/swagger-ui/**",  // Swagger UI
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",  // OpenAPI docs
                                        "/actuator/health",  // Health check — load balancers, k8s probes
                                        "/actuator/info"    // Build/info endpoint — non-sensitive
                                ).permitAll()

                                // Actuator lockdown (Phase 6 Slice 3): metrics + prometheus are
                                // sensitive (expose traffic, JVM internals, endpoint names).
                                // Default-deny ALL actuator paths to ADMIN, then the explicit
                                // permitAll above only re-opens health + info. ORDER MATTERS in
                                // Spring Security: the FIRST matching rule wins, so the public
                                // health/info rules above must come before this catch-all.
                                // NOTE for Hien: if a Prometheus scraper cannot authenticate,
                                // open ONLY "/actuator/prometheus" by adding it to permitAll — do
                                // NOT loosen "/actuator/**" wholesale.
                                .requestMatchers("/actuator/**").hasRole("ADMIN")

                                // Vendor-only endpoints - requires VENDOR role
                                // IMPORTANT: /api/bookings/vendor must come BEFORE /api/** catch-all
                                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/bookings/vendor").hasRole("VENDOR")
                                .requestMatchers("/api/vendor/**").hasRole("VENDOR")

                                // Admin-only endpoints - requires ADMIN role
                                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                                // All other /api/** endpoints require authentication
                                .requestMatchers("/api/**").authenticated()

                                // Allow all other requests (static resources, etc)
                                .anyRequest().permitAll()
                )

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                // JWT filter checks token first, then Spring's default filter handles auth
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Add rate-limit filter FIRST, before JWT (Phase 5).
                // WHY before JWT: /api/auth/** is public (no JWT), so the JWT filter is a no-op there,
                // but a brute-force login attempt must be throttled BEFORE any bcrypt/DB work happens.
                // Placing it first also means a rejected (429) request never reaches the auth service.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration source — which browser origins may call this API.
     *
     * WHY an explicit allow-list (never "*" with credentials):
     * - The API uses JWT in the Authorization header, so cross-origin requests from the browser
     *   carry credentials in spirit. Returning Access-Control-Allow-Origin: * is illegal when
     *   credentials are involved, and returning it blindly would also let any site make
     *   authenticated calls on a logged-in user's behalf (CSRF-adjacent).
     * - Instead we echo back the exact requesting Origin if it is in the allow-list. That is the
     *   safe pattern Spring's CorsConfiguration#applyPermitDefaultValues + setAllowedOriginPatterns
     *   implement under the hood.
     *
     * WHY configurable via app.cors.allowed-origins (comma-separated):
     * - Dev (localhost:3000), preview deploys (*.vercel.app), and prod each have different
     *   origins. Hardcoding would force a redeploy for every environment. An env-driven list lets
     *   Render/Compose/Vercel set it without touching code.
     * - Default empty → NO origins allowed. This is fail-closed: a misconfigured deploy does not
     *   accidentally open the API to the whole internet.
     *
     * setAllowedOriginPatterns (not setAllowedOrigins):
     * - Patterns support wildcards, so APP_CORS_ALLOWED_ORIGINS=https://*.vercel.app permits every
     *   Vercel preview URL without listing each one. Plain setAllowedOrigins would reject the "*".
     *
     * Headers/methods: Authorization + Content-Type cover every REST endpoint we expose; the method
     * list covers GET/POST/PUT/PATCH/DELETE + OPTIONS (preflight). Cache preflight for 1h to cut
     * round-trips (browser max-age).
     *
     * @param allowedOrigins comma-separated origin list from app.cors.allowed-origins (may be empty)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns (not Origins) so wildcards like https://*.vercel.app work.
        config.setAllowedOriginPatterns(parseOrigins(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        // Expose Authorization so a browser app could read it (we don't put JWT there today, but it
        // keeps the contract open for future responses that set a refresh-token cookie header).
        config.setExposedHeaders(List.of("Authorization"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register for every path — same rules for all endpoints, role checks still apply afterwards.
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Split "https://a.com, https://b.com" into a clean list, ignoring blanks.
     * WHY a helper: env var lists are notoriously messy (trailing commas, spaces); splitting naively
     * produces [""] which CorsConfiguration treats as "allow origin ''". Trim + filter empty.
     */
    private List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Password encoder bean for hashing user passwords.
     *
     * WHY: BCrypt is the standard password hashing algorithm.
     * - One-way hash (cannot decrypt)
     * - Salt is built-in (prevents rainbow table attacks)
     * - Adjustable work factor (10 rounds by default)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}