package com.hien.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * Phase 0: Permit all requests — we haven't built auth yet.
 * Phase 2 will add JWT authentication, role-based access, etc.
 *
 * @Configuration tells Spring this class defines beans (objects managed by Spring).
 * @Bean marks a method whose return value is registered as a Spring bean.
 * SecurityFilterChain = the main security rule chain in Spring Security 6+.
 *
 * Why disable CSRF? This is a REST API (stateless), not a server-rendered web app.
 * CSRF protection is for browser-based form submissions. With JWT, it's not needed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless session — no HTTP sessions, each request carries JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Disable CSRF for REST API (we'll use JWT tokens, not cookies)
            .csrf(AbstractHttpConfigurer::disable)
            // Phase 0: permit all requests. Phase 2 will restrict this.
            .authorizeHttpRequests(auth ->
                auth.anyRequest().permitAll()
            );

        return http.build();
    }
}
