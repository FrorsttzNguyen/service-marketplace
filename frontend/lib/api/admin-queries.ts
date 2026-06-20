"use client";

/**
 * TanStack Query hooks + mutations for the admin provider-approval workflow (Slice 7).
 *
 * Mirrors the `bookings-queries.ts` pattern: a centralized query-key factory so
 * invalidation and keying stay in sync, one list query + two mutations. The mutations
 * invalidate the whole `["admin", "providers", ...]` family on success so the list
 * refetches with the new verificationStatus after an approve/reject — including across
 * status filters (a provider leaving PENDING should also drop off the PENDING view even
 * if the user later switches back to it).
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  approveProvider,
  listProviders,
  rejectProvider,
  type ProviderAdmin,
  type ProviderAdminPage,
  type ProviderVerificationStatus,
} from "./admin";

/**
 * Centralized admin-provider query keys. The `list` key includes BOTH the status filter
 * and the page, so switching filter/page creates a fresh cache entry (and the previous
 * one stays cached for back-navigation). `all` is used for broad invalidation on
 * mutations — a single approve/reject changes which list every filter shows.
 */
export const adminProviderKeys = {
  all: ["admin", "providers"] as const,
  lists: () => [...adminProviderKeys.all, "list"] as const,
  list: (
    status: ProviderVerificationStatus | undefined,
    page: number,
    size: number,
  ) => [...adminProviderKeys.lists(), { status, page, size }] as const,
} as const;

/** Query options for useProviders. status=undefined means "all statuses". */
export interface UseProvidersOptions {
  status?: ProviderVerificationStatus;
  page?: number;
  size?: number;
}

/**
 * Fetch the admin provider list, optionally filtered by status. Returns the standard
 * query result so callers can branch on isPending/isError/data. The endpoint requires
 * an ADMIN-role JWT; a 403 surfaces as an error (the RequireAuth gate means we never
 * reach here as a non-admin in normal navigation, but defense in depth still matters).
 */
export function useProviders(
  options: UseProvidersOptions = {},
): UseQueryResult<ProviderAdminPage> {
  const { status, page = 0, size = 10 } = options;
  return useQuery({
    queryKey: adminProviderKeys.list(status, page, size),
    // `signal` is destructured for parity with the other query hooks (bookings/services);
    // the typed wrapper doesn't currently thread it through, matching the established
    // pattern. Wiring it end-to-end is a small future improvement across all resources.
    queryFn: ({ signal }) => listProviders({ status, page, size }),
    staleTime: 30_000,
  });
}

/**
 * Approve a provider. On success, invalidates the entire admin-provider query family so the
 * list refetches and the provider moves out of PENDING. The page handles the optimistic
 * button state + per-row error display (e.g. a 404 if the provider was deleted between
 * the list render and the click).
 */
export function useApproveProvider(): UseMutationResult<
  ProviderAdmin,
  unknown,
  number // provider id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (providerId: number) => approveProvider({ providerId }),
    onSuccess: () => {
      // Broad invalidation: an approve changes every page + every status filter.
      void queryClient.invalidateQueries({ queryKey: adminProviderKeys.all });
    },
  });
}

/**
 * Reject a provider. Same contract + invalidation as useApproveProvider — the provider leaves
 * PENDING either way, so both mutations refresh the same set of queries.
 */
export function useRejectProvider(): UseMutationResult<
  ProviderAdmin,
  unknown,
  number // provider id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (providerId: number) => rejectProvider({ providerId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminProviderKeys.all });
    },
  });
}
