/**
 * Typed admin client — thin wrappers over apiGet/apiPost for the admin provider-approval
 * endpoints (Slice 7).
 *
 * Mirrors the layout of `bookings.ts` / `services.ts`: this file owns the admin domain
 * (path params, the Spring `Page<ProviderAdminResponse>` shape, the verification-status
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

/** A single admin-provider row, typed straight from the generated schema. */
export type ProviderAdmin = components["schemas"]["ProviderAdminResponse"];

/** Spring `Page<ProviderAdminResponse>` shape. Pinned to the generated schema. */
export type ProviderAdminPage = components["schemas"]["PageProviderAdminResponse"];

/** Verification statuses the admin can filter by / act on (mirrors backend enum). */
export type ProviderVerificationStatus = NonNullable<
  ProviderAdmin["verificationStatus"]
>;

/**
 * List providers, optionally filtered by verification status
 * (`GET /api/admin/providers?status=...&page=...&size=...`).
 *
 * Omitting `status` lists ALL providers (any status). The admin UI defaults to PENDING so
 * the reviewer lands on the actionable queue first, but APPROVED/REJECTED/all are a
 * filter switch away. Pagination is 0-based, matching Spring's Pageable.
 */
export interface ListProvidersParams {
  status?: ProviderVerificationStatus;
  page?: number; // 0-based, matches Spring's Pageable
  size?: number;
}

export async function listProviders(
  params: ListProvidersParams = {},
): Promise<ProviderAdminPage> {
  const { status, page = 0, size = 10 } = params;
  return apiGet("/api/admin/providers", {
    query: { status, page, size },
  }) as Promise<ProviderAdminPage>;
}

/** Params for the approve/reject mutations — just the target provider's id. */
export interface ProviderActionParams {
  providerId: number;
}

/**
 * Approve a provider (`POST /api/admin/providers/{providerId}/approve`).
 *
 * Returns the updated provider row (verificationStatus: APPROVED). Possible failures:
 *   - 401 not authenticated
 *   - 403 authenticated but not ADMIN (defense in depth — the UI gates this too)
 *   - 404 provider not found (deleted between list render and click)
 */
export async function approveProvider(
  params: ProviderActionParams,
): Promise<ProviderAdmin> {
  const path = `/api/admin/providers/${encodeURIComponent(params.providerId)}/approve`;
  // No request body for these mutations — apiPost sends an empty JSON body, which the
  // backend accepts (the only meaningful input is the path param).
  return apiPost(path, {}) as Promise<ProviderAdmin>;
}

/**
 * Reject a provider (`POST /api/admin/providers/{providerId}/reject`).
 *
 * Same contract as approveProvider; returns the updated row (verificationStatus: REJECTED).
 */
export async function rejectProvider(
  params: ProviderActionParams,
): Promise<ProviderAdmin> {
  const path = `/api/admin/providers/${encodeURIComponent(params.providerId)}/reject`;
  return apiPost(path, {}) as Promise<ProviderAdmin>;
}
