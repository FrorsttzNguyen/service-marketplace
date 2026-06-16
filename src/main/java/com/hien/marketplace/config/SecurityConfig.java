package com.hien.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.infrastructure.security.RateLimitFilter;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
            @org.springframework.beans.factory.annotation.Value("${app.ratelimit.enabled:true}") boolean enabled
    ) {
        return new RateLimitFilter(proxyManagerProvider, objectMapper, enabled);
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        http
                // Disable CSRF - not needed for stateless JWT API
                .csrf(AbstractHttpConfigurer::disable)

                // Configure session management - STATELESS for JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure endpoint authorization rules
                .authorizeHttpRequests(auth ->
                        auth
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
                                        "/actuator/health"  // Health check
                                ).permitAll()

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