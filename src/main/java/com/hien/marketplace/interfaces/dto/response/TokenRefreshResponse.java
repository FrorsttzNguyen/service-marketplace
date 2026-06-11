package com.hien.marketplace.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for token refresh.
 *
 * WHY: Only returns new access token (not user info).
 * Client already has user context from initial login.
 */
public record TokenRefreshResponse(
    String accessToken,
    LocalDateTime accessTokenExpiresAt
) {
}