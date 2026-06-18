/**
 * Typed admin client — thin wrappers over apiGet/apiPost for the admin vendor-approval
 * endpoints (Slice 7).
 *
 * Mirrors the layout of `bookings.ts` / `services.ts`: this file owns the admin domain
 * (path params, the Spring `Page<VendorAdminResponse>` shape, the verification-status
 * enum), the generic `client.ts` owns HTTP + the JWT/401-refresh plumbing.
 *
 * All three endpoints require an ADMIN-role JWT. `client.ts` auto-attaches the in-memory
 * access token and runs the 401 single-flight refresh, so these wrappers don't touch
 * tokens directly. Role enforcement happens server-side (403 if not an admin); the
 * client also gates the route via <RequireAuth requireRole="ADMIN"> + an ADMIN-only nav
 * link, so a non-admin never reaches these calls in normal navigation.
 */
import { apiGet, apiPost } from "./client";
import type { components } from "./schema";

/** A single admin-vendor row, typed straight from the generated schema. */
export type VendorAdmin = components["schemas"]["VendorAdminResponse"];

/** Spring `Page<VendorAdminResponse>` shape. Pinned to the generated schema. */
export type VendorAdminPage = components["schemas"]["PageVendorAdminResponse"];

/** Verification statuses the admin can filter by / act on (mirrors backend enum). */
export type VendorVerificationStatus = NonNullable<
  VendorAdmin["verificationStatus"]
>;

/**
 * List vendors, optionally filtered by verification status
 * (`GET /api/admin/vendors?status=...&page=...&size=...`).
 *
 * Omitting `status` lists ALL vendors (any status). The admin UI defaults to PENDING so
 * the reviewer lands on the actionable queue first, but APPROVED/REJECTED/all are a
 * filter switch away. Pagination is 0-based, matching Spring's Pageable.
 */
export interface ListVendorsParams {
  status?: VendorVerificationStatus;
  page?: number; // 0-based, matches Spring's Pageable
  size?: number;
}

export async function listVendors(
  params: ListVendorsParams = {},
): Promise<VendorAdminPage> {
  const { status, page = 0, size = 10 } = params;
  return apiGet("/api/admin/vendors", {
    query: { status, page, size },
  }) as Promise<VendorAdminPage>;
}

/** Params for the approve/reject mutations — just the target vendor's id. */
export interface VendorActionParams {
  vendorId: number;
}

/**
 * Approve a vendor (`POST /api/admin/vendors/{vendorId}/approve`).
 *
 * Returns the updated vendor row (verificationStatus: APPROVED). Possible failures:
 *   - 401 not authenticated
 *   - 403 authenticated but not ADMIN (defense in depth — the UI gates this too)
 *   - 404 vendor not found (deleted between list render and click)
 */
export async function approveVendor(
  params: VendorActionParams,
): Promise<VendorAdmin> {
  const path = `/api/admin/vendors/${encodeURIComponent(params.vendorId)}/approve`;
  // No request body for these mutations — apiPost sends an empty JSON body, which the
  // backend accepts (the only meaningful input is the path param).
  return apiPost(path, {}) as Promise<VendorAdmin>;
}

/**
 * Reject a vendor (`POST /api/admin/vendors/{vendorId}/reject`).
 *
 * Same contract as approveVendor; returns the updated row (verificationStatus: REJECTED).
 */
export async function rejectVendor(
  params: VendorActionParams,
): Promise<VendorAdmin> {
  const path = `/api/admin/vendors/${encodeURIComponent(params.vendorId)}/reject`;
  return apiPost(path, {}) as Promise<VendorAdmin>;
}
