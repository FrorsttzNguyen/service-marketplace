package com.hien.marketplace.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * JWT utility class for token generation, validation, and parsing.
 *
 * WHY: Stateless authentication with JWT (JSON Web Token).
 * - No server-side session storage required
 * - Tokens contain user identity + roles
 * - Easily scalable across multiple servers
 *
 * JWT Structure:
 * - Header: algorithm + token type
 * - Payload: claims (userId, email, role, expiration)
 * - Signature: prevents tampering
 *
 * Security:
 * - Tokens signed with HMAC-SHA256 using secret key
 * - Access token: short-lived (15 min) for API calls
 * - Refresh token: long-lived (7 days) for getting new access token
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    // Secret key for signing tokens - MUST be at least 256 bits (32 chars) for HS256
    @Value("${app.jwt.secret:your-very-long-secret-key-must-be-at-least-256-bits-long}")
    private String jwtSecret;

    // Access token expiration in minutes
    @Value("${app.jwt.access-expiration-minutes:15}")
    private int accessTokenExpirationMinutes;

    // Refresh token expiration in days
    @Value("${app.jwt.refresh-expiration-days:7}")
    private int refreshTokenExpirationDays;

    /**
     * Generate access token for authenticated user.
     *
     * Access token is short-lived and used for API calls.
     * Contains: userId, email, role as claims.
     */
    public String generateAccessToken(Long userId, String email, String role) {
        return buildToken(
                userId.toString(),
                email,
                role,
                accessTokenExpirationMinutes * 60 * 1000L  // minutes → milliseconds
        );
    }

    /**
     * Generate refresh token for getting new access tokens.
     *
     * Refresh token is long-lived and only used to get new access tokens.
     * Contains minimal claims (just subject = userId).
     */
    public String generateRefreshToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() +
                        refreshTokenExpirationDays * 24L * 60 * 60 * 1000))  // days → ms
                .signWith(key)
                .compact();
    }

    /**
     * Validate JWT token signature and expiration.
     *
     * WHY: Every API request with token must be validated.
     * Checks: signature valid, not expired, format correct.
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract user ID from JWT token subject.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extract email from JWT token claims.
     */
    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).get("email", String.class);
    }

    /**
     * Extract role from JWT token claims.
     */
    public String getRoleFromToken(String token) {
        return getClaimsFromToken(token).get("role", String.class);
    }

    /**
     * Get access token expiration as LocalDateTime (for response DTO).
     */
    public LocalDateTime getAccessTokenExpiration() {
        return LocalDateTime.now().plusMinutes(accessTokenExpirationMinutes);
    }

    /**
     * Get refresh token expiration as LocalDateTime (for response DTO).
     */
    public LocalDateTime getRefreshTokenExpiration() {
        return LocalDateTime.now().plusDays(refreshTokenExpirationDays);
    }

    // === Private helper methods ===

    private String buildToken(String subject, String email, String role, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(subject)  // userId
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)  // Sign with HMAC-SHA256
                .compact();
    }

    private Claims getClaimsFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}