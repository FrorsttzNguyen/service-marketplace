/**
 * Typed catalog client — thin, intent-revealing wrappers over the generic `apiGet`.
 *
 * Why a separate file per resource area: the generic `apiGet` knows about HTTP; this
 * file knows about the *catalog domain* (Spring's `Page<T>` shape, pagination params,
 * path-param substitution for `/api/services/{id}` and category filtering). Callers
 * (hooks/pages) depend on these names instead of raw path strings, so a backend path
 * rename only touches this file.
 */
import { apiGet } from "./client";
import type { components } from "./schema";

/**
 * A Spring `Page<T>` response. We only read the fields the UI cares about; the rest
 * (`pageable`, `sort`, `first`/`last`...) exist on the wire but are optional in the
 * generated schema. Keeping the alias pinned to the generated `PageServiceResponse`
 * means a spec change flows through here automatically on `gen:api`.
 */
export type ServicePage = components["schemas"]["PageServiceResponse"];

/** A single service row, typed straight from the generated schema (no hand-rolled DTO). */
export type Service = components["schemas"]["ServiceResponse"];

/** Pagination options for the public catalog. Defaults are applied in each list function. */
export interface ListServicesParams {
  page?: number; // 0-based, matches Spring's Pageable
  size?: number;
}

/**
 * Fetch the public, paginated service catalog (`GET /api/services`).
 *
 * Spring expects `page`/`size` as query params (its `Pageable` binder). We forward
 * them verbatim — omitting them here would make the backend use its own defaults,
 * which is fine but makes the UI's notion of "current page" implicit. Explicit is better.
 */
export async function listServices(
  params: ListServicesParams = {},
): Promise<ServicePage> {
  const { page = 0, size = 10 } = params;
  // The cast is safe: the JSON returned by `/api/services` matches ServicePage by contract.
  return apiGet("/api/services", { query: { page, size } }) as Promise<ServicePage>;
}

/** Params for fetching a single service by its id. */
export interface GetServiceParams {
  id: number;
}

/**
 * Fetch one service's details (`GET /api/services/{id}`).
 *
 * The `{id}` segment from the OpenAPI template is substituted here so `apiGet` only
 * ever sees a concrete path. A 404 (service not found) surfaces as an `ApiError`
 * with `status === 404`; the detail page branches on that to distinguish "gone"
 * from a network/CORS failure.
 */
export async function getService(
  params: GetServiceParams,
): Promise<Service> {
  // encodeURIComponent guards against anything weird, though numeric ids are safe.
  const path = `/api/services/${encodeURIComponent(params.id)}`;
  return apiGet(path) as Promise<Service>;
}

/** Params for filtering the catalog by category (`GET /api/services/category/{categoryId}`). */
export interface ListServicesByCategoryParams {
  categoryId: number;
  page?: number;
  size?: number;
}

/**
 * Fetch services filtered by category (`GET /api/services/category/{categoryId}`).
 * Same `Page<T>` shape as the unfiltered catalog, so the same UI (cards + pagination)
 * renders both without branching on the data structure.
 */
export async function listServicesByCategory(
  params: ListServicesByCategoryParams,
): Promise<ServicePage> {
  const { categoryId, page = 0, size = 10 } = params;
  const path = `/api/services/category/${encodeURIComponent(categoryId)}`;
  return apiGet(path, { query: { page, size } }) as Promise<ServicePage>;
}

/**
 * A category, derived from the fields the backend already puts on each Service row.
 *
 * IMPORTANT: there is no `GET /api/categories` endpoint. We do NOT call one. Instead
 * we derive the distinct set of categories from `categoryId` + `categoryName` present
 * on every loaded `ServiceResponse` (see `deriveCategories`). This keeps the filter
 * list honest with whatever the catalog actually contains — no phantom categories.
 */
export interface Category {
  id: number;
  name: string;
}

/**
 * Derive the distinct list of categories from a set of service rows.
 *
 * Why derive instead of fetching: (1) there's no categories endpoint to fetch, and
 * (2) building it from the rows guarantees the chips only show categories that have
 * real services right now. Rows missing a name fall back to `"Category #<id>"` so a
 * missing name never blanks a filter; rows missing an id are skipped (can't link them).
 * Stable sort by id so chip order doesn't jump between pages.
 *
 * Exported for unit testing later (it's a pure function — easy to test in isolation).
 */
export function deriveCategories(services: Service[]): Category[] {
  const byId = new Map<number, Category>();
  for (const service of services) {
    const id = service.categoryId;
    if (id === undefined || id === null) continue; // can't build a category link without an id
    if (byId.has(id)) continue; // first occurrence wins; keeps name stable
    byId.set(id, { id, name: service.categoryName || `Category #${id}` });
  }
  return [...byId.values()].sort((a, b) => a.id - b.id);
}
