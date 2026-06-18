/**
 * Typed vendor-services client — thin wrappers over apiGet/apiPost/apiPut/apiDelete
 * for the vendor's own-service management endpoints.
 *
 * Mirrors the layout of `admin.ts` / `bookings.ts`: this file owns the vendor-service
 * domain (path params, the Spring `Page<ServiceResponse>` shape, the create/update
 * request bodies, the service status enum), the generic `client.ts` owns HTTP + the
 * JWT/401-refresh plumbing.
 *
 * All endpoints require a VENDOR-role JWT. `client.ts` auto-attaches the in-memory
 * access token and runs the 401 single-flight refresh, so these wrappers don't touch
 * tokens directly. Role enforcement happens server-side (403 if not a vendor); the
 * client also gates the route via <RequireAuth requireRole="VENDOR"> + a VENDOR-only
 * nav link, so a non-vendor never reaches these calls in normal navigation.
 *
 * SCOPE (per the backend contract):
 *   - GET    /api/vendor/services?page&size           -> Page<ServiceResponse> (ALL statuses)
 *   - POST   /api/vendor/services                     -> ServiceResponse (created DRAFT)
 *   - PUT    /api/vendor/services/{id}                -> ServiceResponse (updated fields)
 *   - POST   /api/vendor/services/{id}/activate       -> ServiceResponse (status ACTIVE)
 *   - DELETE /api/vendor/services/{id}                -> 204 (deactivate -> INACTIVE)
 * Note: DELETE here is a SOFT delete (deactivate). The row stays in the DB and the
 * list still shows it with status INACTIVE; it just leaves the public catalog.
 */
import { apiDelete, apiGet, apiPost, apiPut } from "./client";
import type { components } from "./schema";

/** A single vendor-service row, typed straight from the generated schema. */
export type VendorService = components["schemas"]["ServiceResponse"];

/** Spring `Page<ServiceResponse>` shape. Pinned to the generated schema. */
export type VendorServicePage = components["schemas"]["PageServiceResponse"];

/** Body for creating a service (categoryId/title/pricingType/basePrice required). */
export type ServiceCreateRequest = components["schemas"]["ServiceCreateRequest"];

/** Body for updating a service (all fields optional; only changed fields are sent). */
export type ServiceUpdateRequest = components["schemas"]["ServiceUpdateRequest"];

/** The service lifecycle statuses a vendor can see/act on (mirrors backend enum). */
export type ServiceStatus = NonNullable<VendorService["status"]>;

/** The pricing models a service can use (mirrors backend enum). */
export type PricingType = NonNullable<VendorService["pricingType"]>;

/** Pagination options for the vendor's service list. */
export interface ListVendorServicesParams {
  page?: number; // 0-based, matches Spring's Pageable
  size?: number;
}

/**
 * List the authenticated vendor's own services (`GET /api/vendor/services`).
 *
 * Unlike the public catalog (which only shows ACTIVE), this endpoint returns services
 * in ALL statuses (DRAFT/ACTIVE/INACTIVE) so the vendor can manage their full
 * inventory — drafts in progress, live listings, and deactivated items. The endpoint
 * is JWT-scoped server-side (the vendor id comes from the token), so there's no
 * vendorId param to pass.
 */
export async function listVendorServices(
  params: ListVendorServicesParams = {},
): Promise<VendorServicePage> {
  const { page = 0, size = 10 } = params;
  return apiGet("/api/vendor/services", {
    query: { page, size },
  }) as Promise<VendorServicePage>;
}

/** Params for creating a service. */
export interface CreateVendorServiceParams {
  body: ServiceCreateRequest;
}

/**
 * Create a new service (`POST /api/vendor/services`).
 *
 * New services are created in DRAFT status (the vendor must explicitly Activate to
 * publish). The backend validates: categoryId required, title 5–200 chars,
 * description ≤ 2000, pricingType in {FIXED,HOURLY,VARIABLE}, basePrice > 0,
 * durationHours integer > 0 (when provided), address ≤ 500, city ≤ 50. Mismatches
 * surface as 400 with a Spring validation message; the form also validates client-side
 * to give instant feedback and avoid a wasted round-trip.
 *
 * Possible failures:
 *   - 400 invalid input (caught by form validation first, but defense in depth)
 *   - 401/403 not a vendor (the route gate + 403 cover this)
 */
export async function createVendorService(
  params: CreateVendorServiceParams,
): Promise<VendorService> {
  return apiPost("/api/vendor/services", params.body) as Promise<VendorService>;
}

/** Params for updating a service. */
export interface UpdateVendorServiceParams {
  id: number;
  body: ServiceUpdateRequest;
}

/**
 * Update an existing service (`PUT /api/vendor/services/{id}`).
 *
 * All fields are optional in the request body; only the fields the vendor changed are
 * sent (the form constructs the body from dirty fields). categoryId is NOT part of the
 * update request (the backend doesn't allow re-categorizing a live service), so the
 * form's category dropdown is disabled in edit mode.
 *
 * Possible failures:
 *   - 400 invalid input
 *   - 403 not the owner of this service (another vendor's id, or not a vendor)
 *   - 404 service not found (deleted between list render and submit)
 */
export async function updateVendorService(
  params: UpdateVendorServiceParams,
): Promise<VendorService> {
  const path = `/api/vendor/services/${encodeURIComponent(params.id)}`;
  return apiPut(path, params.body) as Promise<VendorService>;
}

/** Params for activating a service. */
export interface ActivateVendorServiceParams {
  id: number;
}

/**
 * Activate (publish) a service (`POST /api/vendor/services/{id}/activate`).
 *
 * Transitions status DRAFT|INACTIVE → ACTIVE so the service appears in the public
 * catalog and becomes bookable. No request body — the only meaningful input is the
 * path param. The UI gates the Activate button to DRAFT/INACTIVE-only; activating an
 * already-ACTIVE service is a no-op the backend tolerates but the UI avoids to keep
 * the action meaningful.
 *
 * Possible failures:
 *   - 403 not the owner / not a vendor
 *   - 404 service not found
 */
export async function activateVendorService(
  params: ActivateVendorServiceParams,
): Promise<VendorService> {
  const path = `/api/vendor/services/${encodeURIComponent(params.id)}/activate`;
  // Empty body for this POST — apiPost sends an empty JSON body, which the backend
  // accepts (the only meaningful input is the path param).
  return apiPost(path, {}) as Promise<VendorService>;
}

/** Params for deactivating a service. */
export interface DeactivateVendorServiceParams {
  id: number;
}

/**
 * Deactivate (soft delete) a service (`DELETE /api/vendor/services/{id}`).
 *
 * Despite the DELETE verb, this is a SOFT delete: status ACTIVE → INACTIVE. The row
 * stays in the vendor's list (as INACTIVE) and remains attached to historical
 * bookings; it just leaves the public catalog and is no longer bookable. The vendor
 * can re-Activate it later.
 *
 * Returns 204 No Content (no body) — `apiDelete` resolves `void` on success, so this
 * wrapper returns `Promise<void>` to make the contract explicit at the type level.
 *
 * Possible failures:
 *   - 403 not the owner / not a vendor
 *   - 404 service not found
 */
export async function deactivateVendorService(
  params: DeactivateVendorServiceParams,
): Promise<void> {
  const path = `/api/vendor/services/${encodeURIComponent(params.id)}`;
  await apiDelete(path);
}
