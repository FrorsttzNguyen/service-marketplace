"use client";

/**
 * TanStack Query hook for the public categories list (`GET /api/categories`).
 *
 * Why a dedicated (tiny) file: the vendor create-service form needs the same list
 * of categories in a `<select>`, and keeping the query in a hook (instead of fetching
 * inline in the form) means TanStack Query caches it across mounts — opening the form
 * twice doesn't refetch, and switching between Edit/New on the same page reuses the
 * cache. The pattern mirrors `bookings-queries.ts` / `admin-queries.ts`.
 *
 * The query key is a constant array under `["categories"]` — there are no params to
 * a "list all categories" call, so a single cache entry is correct. staleTime of 5m
 * reflects that categories change very rarely (admin-managed), so we avoid refetching
 * on every form open within a session.
 */
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { listCategories, type Category } from "./categories";

/** Centralized categories query key. No params → a single entry. */
export const categoryKeys = {
  all: ["categories"] as const,
  list: () => [...categoryKeys.all, "list"] as const,
} as const;

/**
 * Fetch all service categories for use in a dropdown / filter.
 *
 * Returns the standard query result so the form can branch on
 * isPending/isError/data. `enabled` defaults to true; pass `{ enabled: false }` if
 * you want to defer the fetch (not currently needed by the vendor form, which always
 * needs categories when it's open).
 */
export function useCategories(): UseQueryResult<Category[]> {
  return useQuery({
    queryKey: categoryKeys.list(),
    // `signal` is destructured for parity with the other query hooks (bookings/services);
    // the typed wrapper doesn't currently thread it through, matching the established
    // pattern across all resource hooks.
    queryFn: ({ signal }) => listCategories(),
    // Categories are admin-managed and change rarely → cache aggressively so repeated
    // form opens in the same session don't refetch. 5 minutes balances freshness vs
    // a sensible ceiling (an admin adding a category mid-session will show up within 5m).
    staleTime: 5 * 60 * 1000,
  });
}
