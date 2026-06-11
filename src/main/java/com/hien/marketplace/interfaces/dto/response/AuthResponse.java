package com.hien.marketplace.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for successful authentication (register/login).
 *
 * WHY: Returns JWT tokens + basic user info so client can:
 * - Store tokens for subsequent API calls
 * - Display user name immediately without another API call
 *
 * Security note: Never return password or sensitive data in response.
 */
public record AuthResponse(
    Long userId,
    String fullName,
    String email,
    String role,  // CUSTOMER, VENDOR, ADMIN
    String accessToken,
    String refreshToken,
    LocalDateTime accessTokenExpiresAt,
    LocalDateTime refreshTokenExpiresAt
) {
}