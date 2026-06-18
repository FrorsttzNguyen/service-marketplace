"use client";

/**
 * TanStack Query hooks + mutations for the vendor side of bookings (Phase 7 Slice 6).
 *
 * Mirrors `bookings-queries.ts` (the customer side): a centralized query-key factory,
 * one list query, and one mutation. The keys live under a SEPARATE `["vendor",
 * "bookings"]` family from the customer `["bookings"]` family, so a confirm on the
 * vendor side doesn't force the customer list to refetch (and vice versa) — they're
 * genuinely different queries against different endpoints, even though they share the
 * Booking wire shape.
 *
 * The confirm mutation reuses the `cancelBooking`-style pattern: a single id input,
 * broad invalidation on success, and the page owns the per-row spinner + 422 "status
 * changed" messaging.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  completeBooking,
  confirmBooking,
  listVendorBookings,
  startBooking,
  type VendorBookingPage,
} from "./vendor-bookings";
import type { Booking } from "./bookings";

/**
 * Centralized vendor-booking query keys. Kept DISTINCT from the customer `bookingKeys`
 * in `bookings-queries.ts` — same resource concept, different endpoint + viewer, so a
 * cache namespace per viewer avoids cross-invalidation surprises.
 */
export const vendorBookingKeys = {
  all: ["vendor", "bookings"] as const,
  lists: () => [...vendorBookingKeys.all, "list"] as const,
  list: (page: number, size: number) =>
    [...vendorBookingKeys.lists(), { page, size }] as const,
} as const;

/** Pagination options for the vendor's booking list. */
export interface UseVendorBookingsOptions {
  page?: number;
  size?: number;
}

/**
 * Fetch the bookings on the authenticated vendor's services (paginated, ALL statuses).
 * Returns the standard query result so callers can branch on isPending/isError/data.
 * The endpoint requires a VENDOR-role JWT; a 403 surfaces as an error.
 */
export function useVendorBookings(
  options: UseVendorBookingsOptions = {},
): UseQueryResult<VendorBookingPage> {
  const { page = 0, size = 10 } = options;
  return useQuery({
    queryKey: vendorBookingKeys.list(page, size),
    // `signal` is destructured for parity with the other query hooks (bookings/services/
    // admin/vendor-services); the typed wrapper doesn't currently thread it through.
    queryFn: ({ signal }) => listVendorBookings({ page, size }),
    staleTime: 30_000,
  });
}

/**
 * Confirm a pending booking by id (PUT /api/bookings/{id}/confirm). On success,
 * invalidates the vendor-bookings family so the list re-fetches with the new CONFIRMED
 * status (and the row's Confirm button disappears, since it's PENDING-gated).
 *
 * The page is responsible for the PENDING-only gate + the 422 "status changed"
 * handling — the same shape as the customer-side cancel mutation.
 */
export function useConfirmBooking(): UseMutationResult<
  Booking,
  unknown,
  number // booking id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => confirmBooking({ id }),
    onSuccess: () => {
      // Broad invalidation within the vendor namespace: a confirm changes every page
      // of the vendor's list (the row's status flips), so we don't pin to the current page.
      void queryClient.invalidateQueries({ queryKey: vendorBookingKeys.all });
    },
  });
}

/**
 * Start a confirmed booking by id (PUT /api/bookings/{id}/start). On success,
 * invalidates the vendor-bookings family so the list re-fetches with the new
 * IN_PROGRESS status (and the row's button flips from "Start" to "Complete").
 *
 * Same contract + invalidation shape as `useConfirmBooking` — the only difference is
 * the transition (CONFIRMED → IN_PROGRESS) and the source-status gate on the page.
 */
export function useStartBooking(): UseMutationResult<
  Booking,
  unknown,
  number // booking id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => startBooking({ id }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: vendorBookingKeys.all });
    },
  });
}

/**
 * Complete an in-progress booking by id (PUT /api/bookings/{id}/complete). On success,
 * invalidates the vendor-bookings family so the list re-fetches with the new COMPLETED
 * status (the row becomes read-only — COMPLETED has no further vendor action).
 *
 * Completing is the final vendor step and is what unblocks the customer's review flow,
 * so this transition is the natural endpoint of the vendor pipeline.
 */
export function useCompleteBooking(): UseMutationResult<
  Booking,
  unknown,
  number // booking id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => completeBooking({ id }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: vendorBookingKeys.all });
    },
  });
}
