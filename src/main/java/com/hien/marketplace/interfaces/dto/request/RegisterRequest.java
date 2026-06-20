package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for user registration.
 *
 * WHY: DTO isolates external API contract from internal domain model.
 * - Validation happens at DTO layer before data reaches services
 * - Domain entities remain clean without validation annotations
 * - API contract can evolve independently from domain model
 *
 * Jakarta Validation annotations:
 * - @NotBlank: Reject null, empty string, whitespace-only
 * - @Size: Enforce min/max length
 * - @Email: Basic email format validation
 * - @Pattern: Regex validation for custom rules (e.g., password complexity)
 */
public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Password must contain at least one lowercase, one uppercase, and one digit"
    )
    String password,

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+?[1-9]\\d{1,14}$",
        message = "Invalid phone number format (E.164 standard)"
    )
    String phoneNumber,

    // Optional: User can register as provider immediately or later
    Boolean registerAsProvider
) {
}