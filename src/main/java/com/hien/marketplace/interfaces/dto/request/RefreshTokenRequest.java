package com.hien.nguyen.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for refreshing JWT access token.
 *
 * WHY: JWT tokens have expiration for security. Refresh mechanism:
 * - Access token: Short-lived (e.g., 15 min) — used for API calls
 * - Refresh token: Long-lived (e.g., 7 days) — used to get new access token
 *
 * This avoids forcing users to re-login frequently while maintaining security.
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}