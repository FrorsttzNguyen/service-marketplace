package com.hien.marketplace.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter that runs once per request.
 *
 * WHY: Intercept every HTTP request to check for JWT token in header.
 * If valid token found, set authentication in Spring Security context.
 *
 * HOW IT WORKS:
 * 1. Extract JWT from Authorization: Bearer <token> header
 * 2. Validate token signature and expiration
 * 3. Extract userId, email, role from token
 * 4. Create Authentication object and set in SecurityContext
 * 5. Continue filter chain (request proceeds to controller)
 *
 * WHY extends OncePerRequestFilter:
 * - Ensures filter runs only once per request
 * - Works correctly with async/dispatch scenarios
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Step 1: Extract JWT from Authorization header
            String jwt = extractJwtFromRequest(request);

            // Step 2: Validate token and set authentication
            if (StringUtils.hasText(jwt) && jwtUtils.validateToken(jwt)) {
                // Extract user info from token
                Long userId = jwtUtils.getUserIdFromToken(jwt);
                String email = jwtUtils.getEmailFromToken(jwt);
                String role = jwtUtils.getRoleFromToken(jwt);

                // Create UserDetails-like principal (simplified - just userId)
                UserPrincipal principal = new UserPrincipal(userId, email, role);

                // Create Authentication object
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,  // credentials - not needed after auth
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                // Set in SecurityContext - controller can access via @AuthenticationPrincipal
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Set authentication for user {} with role {}", userId, role);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
            // Don't throw - allow request to proceed (will fail if endpoint requires auth)
        }

        // Step 3: Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT from Authorization header.
     *
     * Expected format: "Authorization: Bearer <token>"
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // Remove "Bearer " prefix
        }

        return null;
    }

    /**
     * Simple principal class to hold user info from JWT.
     * Used as the "principal" in Authentication object.
     */
    public record UserPrincipal(Long userId, String email, String role) {
    }
}