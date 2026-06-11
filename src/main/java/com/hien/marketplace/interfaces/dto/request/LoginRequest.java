package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user login.
 *
 * WHY: Separate DTO for login because:
 * - Login has different validation rules than registration (no password complexity)
 * - Login returns JWT tokens, not user profile
 * - Keeps API contract explicit and focused
 */
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {
}