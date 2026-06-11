package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.user.UserStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for User entity.
 *
 * WHY: Never expose User entity directly to controllers because:
 * - Entity has password hash (security risk)
 * - Entity may have lazy-loaded relationships (N+1 issues)
 * - API contract should be stable even if domain model changes
 *
 * DTO exposes only what API consumers need.
 */
public record UserResponse(
    Long id,
    String fullName,
    String email,
    String phoneNumber,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}