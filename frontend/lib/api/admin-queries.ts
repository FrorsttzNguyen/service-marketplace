"use client";

/**
 * TanStack Query hooks + mutations for the admin vendor-approval workflow (Slice 7).
 *
 * Mirrors the `bookings-queries.ts` pattern: a centralized query-key factory so
 * invalidation and keying stay in sync, one list query + two mutations. The mutations
 * invalidate the whole `["admin", "vendors", ...]` family on success so the list
 * refetches with the new verificationStatus after an approve/reject — including across
 * status filters (a vendor leaving PENDING should also drop off the PENDING view even
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
  approveVendor,
  listVendors,
  rejectVendor,
  type VendorAdmin,
  type VendorAdminPage,
  type VendorVerificationStatus,
} from "./admin";

/**
 * Centralized admin-vendor query keys. The `list` key includes BOTH the status filter
 * and the page, so switching filter/page creates a fresh cache entry (and the previous
 * one stays cached for back-navigation). `all` is used for broad invalidation on
 * mutations — a single approve/reject changes which list every filter shows.
 */
export const adminVendorKeys = {
  all: ["admin", "vendors"] as const,
  lists: () => [...adminVendorKeys.all, "list"] as const,
  list: (
    status: VendorVerificationStatus | undefined,
    page: number,
    size: number,
  ) => [...adminVendorKeys.lists(), { status, page, size }] as const,
} as const;

/** Query options for useVendors. status=undefined means "all statuses". */
export interface UseVendorsOptions {
  status?: VendorVerificationStatus;
  page?: number;
  size?: number;
}

/**
 * Fetch the admin vendor list, optionally filtered by status. Returns the standard
 * query result so callers can branch on isPending/isError/data. The endpoint requires
 * an ADMIN-role JWT; a 403 surfaces as an error (the RequireAuth gate means we never
 * reach here as a non-admin in normal navigation, but defense in depth still matters).
 */
export function useVendors(
  options: UseVendorsOptions = {},
): UseQueryResult<VendorAdminPage> {
  const { status, page = 0, size = 10 } = options;
  return useQuery({
    queryKey: adminVendorKeys.list(status, page, size),
    // `signal` is destructured for parity with the other query hooks (bookings/services);
    // the typed wrapper doesn't currently thread it through, matching the established
    // pattern. Wiring it end-to-end is a small future improvement across all resources.
    queryFn: ({ signal }) => listVendors({ status, page, size }),
    staleTime: 30_000,
  });
}

/**
 * Approve a vendor. On success, invalidates the entire admin-vendor query family so the
 * list refetches and the vendor moves out of PENDING. The page handles the optimistic
 * button state + per-row error display (e.g. a 404 if the vendor was deleted between
 * the list render and the click).
 */
export function useApproveVendor(): UseMutationResult<
  VendorAdmin,
  unknown,
  number // vendor id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vendorId: number) => approveVendor({ vendorId }),
    onSuccess: () => {
      // Broad invalidation: an approve changes every page + every status filter.
      void queryClient.invalidateQueries({ queryKey: adminVendorKeys.all });
    },
  });
}

/**
 * Reject a vendor. Same contract + invalidation as useApproveVendor — the vendor leaves
 * PENDING either way, so both mutations refresh the same set of queries.
 */
export function useRejectVendor(): UseMutationResult<
  VendorAdmin,
  unknown,
  number // vendor id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vendorId: number) => rejectVendor({ vendorId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminVendorKeys.all });
    },
  });
}
