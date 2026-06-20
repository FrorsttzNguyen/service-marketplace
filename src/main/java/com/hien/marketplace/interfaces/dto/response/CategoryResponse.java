package com.hien.marketplace.interfaces.dto.response;

/**
 * Response DTO for a service category (public reference data).
 *
 * WHY: the catalog category filter and the provider "create service" form both need the
 * full list of categories. There was no way to read them (no endpoint), so the catalog
 * had to derive categories from loaded services — which only surfaced categories that
 * already had ACTIVE services. This DTO backs a proper {@code GET /api/categories}.
 */
public record CategoryResponse(
    Long id,
    String name,
    String slug,
    String description
) {
}
