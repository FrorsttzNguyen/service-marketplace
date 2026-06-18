/**
 * Typed categories client — thin wrapper over `apiGet` for the public categories
 * endpoint (`GET /api/categories`).
 *
 * Mirrors the layout of `services.ts` / `bookings.ts`: this file owns the category
 * domain (the `CategoryResponse` shape from the generated schema), the generic
 * `client.ts` owns HTTP + the JWT/401-refresh plumbing.
 *
 * PUBLIC ENDPOINT: `GET /api/categories` is not behind a role gate — it's the same
 * data the public catalog filter uses, and the vendor create-service form reuses it
 * to populate its category dropdown. No token is required, but `client.ts` will still
 * attach one if present (harmless for a public read).
 *
 * NOTE: `services.ts` historically *derived* categories from loaded service rows
 * (see `deriveCategories`) because there was no categories endpoint. That derivation
 * is still used by the public catalog filter (it keeps the chips honest with the
 * visible catalog). This file is the NEW authoritative source for the vendor form's
 * dropdown, where we want every category the backend knows about — including ones
 * with zero published services yet.
 */
import { apiGet } from "./client";
import type { components } from "./schema";

/**
 * A single category row, typed straight from the generated schema.
 * All fields are optional in the schema (Spring serializes nulls), but in practice
 * `id` and `name` are always present for a real category.
 */
export type Category = components["schemas"]["CategoryResponse"];

/**
 * Fetch all service categories (`GET /api/categories`).
 *
 * The endpoint returns a flat array (not a `Page<T>`) — categories are a small,
 * bounded set, so the backend doesn't paginate them. The vendor form renders them
 * all in a `<select>`.
 *
 * Possible failures:
 *   - Network/CORS error (local dev misconfiguration) → ApiError with status 0
 *   - 5xx server error → ApiError with the matching status
 * The page surfaces these via the shared <ErrorState> / per-form message.
 */
export async function listCategories(): Promise<Category[]> {
  // The cast is safe: the JSON returned by `/api/categories` matches Category[] by
  // contract (the spec declares `type: array, items: CategoryResponse`).
  return apiGet("/api/categories") as Promise<Category[]>;
}
