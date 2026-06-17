/**
 * Typed catalog client — thin, intent-revealing wrappers over the generic `apiGet`.
 *
 * Why a separate file per resource area: the generic `apiGet` knows about HTTP; this
 * file knows about the *catalog domain* (Spring's `Page<T>` shape, pagination params).
 * Callers (hooks/pages) depend on these names instead of raw path strings, so a
 * backend path rename only touches this file.
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

/** Pagination options for the public catalog. Defaults are applied in `listServices`. */
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
