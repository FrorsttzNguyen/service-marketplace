package com.hien.marketplace.interfaces.dto.response;

import com.hien.marketplace.domain.vendor.VerificationStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for the admin vendor-approval endpoints (Phase 5.5).
 *
 * WHY a separate DTO (not reusing {@link VendorResponse}):
 * - The admin view is about a vendor's *verification state*, not the public profile shown to
 *   customers. We only need the fields an admin acts on: who, what business, what status.
 * - It exposes the vendor's login email so an admin can contact the vendor during review — that
 *   is admin-only data we intentionally do NOT put in the public {@code VendorResponse}.
 *
 * WHY a Java record:
 * - Immutable carrier of data (no setters, no mutation) — matches the other response DTOs in
 *   this package and the project's value-object style.
 * - Records auto-generate the constructor, accessors, equals/hashCode/toString.
 */
public record VendorAdminResponse(
        Long vendorId,
        Long userId,
        String businessName,
        String email,                     // vendor's login email (admin-only)
        VerificationStatus verificationStatus,
        LocalDateTime createdAt
) {
}
