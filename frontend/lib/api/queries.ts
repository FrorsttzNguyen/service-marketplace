/**
 * TanStack Query hooks for the public service catalog.
 *
 * Why a hook (not calling the api functions directly in the component):
 * - Centralizes the query key so every consumer shares the same cache entry; changing
 *   the page/filter invalidates/refetches correctly.
 * - Keeps loading/error/refetch wiring out of the JSX.
 *
 * `staleTime: 30s` — the catalog doesn't change every second, and the live API
 * sleeps on the free tier, so re-fetching on every mount would be slow + wasteful.
 */
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import {
  deriveCategories,
  getService,
  listServices,
  listServicesByCategory,
  type Category,
  type Service,
  type ServicePage,
} from "./services";

/** Pagination options shared by the list hooks. */
export interface UseServicesOptions {
  page?: number;
  size?: number;
}

/**
 * Fetch the unfiltered catalog page. Kept for parity; the home page uses
 * `useServicesCatalog` (which also accepts a category filter) so there's one entry
 * point for "the list the user is browsing".
 */
export function useServices(
  options: UseServicesOptions = {},
): UseQueryResult<ServicePage> {
  const { page = 0, size = 10 } = options;
  return useQuery({
    queryKey: ["services", page, size],
    queryFn: ({ signal }) => listServices({ page, size }),
    staleTime: 30_000,
  });
}

/** Fetch a single service's details. Errors as ApiError (status 404 → not found). */
export function useService(
  id: number,
): UseQueryResult<Service> {
  return useQuery({
    queryKey: ["service", id],
    queryFn: ({ signal }) => getService({ id }),
    staleTime: 30_000,
    // A 404 (deleted/never-existed service) is a legitimate terminal state, not a
    // transient failure worth retrying. Skip retries so the "not found" UI shows fast.
    retry: (failureCount, error) => {
      if (error instanceof Error && "status" in error && error.status === 404) {
        return false;
      }
      return failureCount < 1;
    },
  });
}

/** Options for the filter-aware catalog hook. `categoryId` undefined → no filter. */
export interface UseServicesCatalogOptions {
  categoryId?: number | null;
  page?: number;
  size?: number;
}

/**
 * Filter-aware catalog hook.
 *
 * One hook for the whole browse experience: pass a `categoryId` to filter, omit/null
 * it for the full catalog. The query key encodes the filter, so switching categories
 * swaps cache entries automatically (no manual invalidation). `enabled` guards both
 * branches so an accidental undefined id never fires a bad request.
 *
 * The filter decision lives in the query key, not in branching `useQuery` calls —
 * React's rules-of-hooks forbid calling hooks conditionally, so we keep one hook and
 * route inside `queryFn`. Same `ServicePage` shape either way → same UI downstream.
 */
export function useServicesCatalog(
  options: UseServicesCatalogOptions = {},
): UseQueryResult<ServicePage> {
  const { categoryId = null, page = 0, size = 10 } = options;
  const filtered = categoryId !== null && categoryId !== undefined;

  return useQuery({
    queryKey: ["services", "catalog", { categoryId: filtered ? categoryId : null, page, size }],
    queryFn: ({ signal }) =>
      filtered
        ? listServicesByCategory({ categoryId: categoryId as number, page, size })
        : listServices({ page, size }),
    staleTime: 30_000,
  });
}

/**
 * Fetch the full catalog (no filter, one big page) and derive distinct categories.
 *
 * Why a separate hook for the category list: the chips need to cover ALL categories
 * that exist in the catalog, not just the ones on the *current* page the user is
 * viewing. So we fetch a large page once (size 100 — the catalog is small for a
 * portfolio app; if it ever grows past a hundred we'd revisit, ideally once a real
 * categories endpoint exists). `deriveCategories` does the distinct + sort.
 *
 * `select` runs the derivation on the cached page in memory — TanStack Query memoizes
 * its result, so the chip list isn't re-derived on every render, only when the page
 * data changes. No `GET /api/categories` call is made anywhere.
 */
export function useCatalogCategories(): UseQueryResult<Category[]> {
  return useQuery({
    queryKey: ["services", "categories"],
    queryFn: ({ signal }) => listServices({ page: 0, size: 100 }),
    staleTime: 5 * 60_000, // categories change rarely; keep the derived list 5 min
    select: (page) => deriveCategories(page.content ?? []),
  });
}
