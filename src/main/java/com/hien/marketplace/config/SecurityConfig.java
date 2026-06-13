package com.hien.marketplace.config;

import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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
     * Main security filter chain configuration.
     *
     * WHY: Defines the security rules for all HTTP requests.
     *
     * SessionCreationPolicy.STATELESS:
     * - No HTTP session created (JWT is stateless)
     * - Each request must include JWT token
     * - Server doesn't store user session
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                                // Vendor booking management actions (confirm, start, complete)
                                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/bookings/*/confirm").hasRole("VENDOR")
                                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/bookings/*/start").hasRole("VENDOR")
                                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/bookings/*/complete").hasRole("VENDOR")
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
