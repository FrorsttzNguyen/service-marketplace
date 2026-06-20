package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating/updating provider profile.
 *
 * WHY: Separate from registration because:
 * - User can become provider later (not only at registration)
 * - Provider profile has different fields than user profile
 * - Provider needs business verification
 */
public record ProviderProfileRequest(
    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 200, message = "Business name must be between 2 and 200 characters")
    String businessName,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    @Size(max = 500, message = "Address must not exceed 500 characters")
    String address,

    @Size(max = 50, message = "City must not exceed 50 characters")
    String city,

    // Bank account for payouts (optional during creation, required before accepting payments)
    @Pattern(regexp = "^\\d{6,20}$", message = "Invalid bank account number")
    String bankAccountNumber,

    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    String bankName
) {
}