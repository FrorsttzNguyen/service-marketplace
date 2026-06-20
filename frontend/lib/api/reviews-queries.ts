"use client";

/**
 * TanStack Query hooks + mutation for reviews.
 *
 * Mirrors the `bookings-queries.ts` / `provider-bookings-queries.ts` pattern: a
 * centralized query-key factory, two read queries (service + provider), and one create
 * mutation. The keys live under a `["reviews", ...]` family scoped by target so that
 * creating a review invalidates only the affected caches.
 *
 * The create mutation invalidates BOTH:
 *   - the reviews family (so the service-detail reviews list picks up the new review),
 *   - the CUSTOMER `["bookings"]` family (so "My bookings" can reflect any post-review
 *     state changes — e.g. a future "reviewed" flag would render correctly without a
 *     manual refetch). Cross-family invalidation is intentional here: a review is the
 *     terminal step of a booking, so the booking view is stale once it's filed.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  createReview,
  listServiceReviews,
  listProviderReviews,
  type Review,
  type ReviewCreateRequest,
} from "./reviews";
import { bookingKeys } from "./bookings-queries";

/**
 * Centralized review query keys. Two read dimensions — by service and by provider — each
 * keyed by its target id so different services/providers get independent cache entries.
 * `all` is used for broad invalidation on a successful create.
 */
export const reviewKeys = {
  all: ["reviews"] as const,
  service: (serviceId: number) =>
    [...reviewKeys.all, "service", serviceId] as const,
  provider: (providerId: number) =>
    [...reviewKeys.all, "provider", providerId] as const,
} as const;

/**
 * Fetch the public reviews for a service. Returns the standard query result so the
 * service detail page can branch on isPending/isError/data. PUBLIC endpoint — no auth.
 *
 * `enabled` is pinned to a valid id so a malformed route param (NaN) doesn't fire a
 * doomed request; the detail page already guards against that, but defense in depth.
 */
export function useServiceReviews(
  serviceId: number,
): UseQueryResult<Review[]> {
  const enabled = Number.isInteger(serviceId) && serviceId > 0;
  return useQuery({
    queryKey: reviewKeys.service(serviceId),
    // `signal` is destructured for parity with the other query hooks (bookings/services/
    // admin); the typed wrapper doesn't currently thread it through, matching the
    // established pattern across all resource hooks.
    queryFn: ({ signal }) => listServiceReviews({ serviceId }),
    enabled,
    // Reviews are appended-only and change rarely once written; cache 1 minute so a
    // visitor navigating away and back doesn't refetch the same list immediately.
    staleTime: 60_000,
  });
}

/**
 * Fetch the public reviews for a provider. Same shape as `useServiceReviews`. Reserved for
 * a future provider-profile page (no UI wires it yet); included for completeness so that
 * page can drop it in without touching this file.
 */
export function useProviderReviews(
  providerId: number,
): UseQueryResult<Review[]> {
  const enabled = Number.isInteger(providerId) && providerId > 0;
  return useQuery({
    queryKey: reviewKeys.provider(providerId),
    queryFn: ({ signal }) => listProviderReviews({ providerId }),
    enabled,
    staleTime: 60_000,
  });
}

/**
 * Create a review. On success, invalidates the reviews family AND the customer bookings
 * family — a review is the terminal step of a booking, so both views are stale once it's
 * filed (the service-detail reviews list needs the new row; "My bookings" may need to
 * reflect a post-review state). The page (not the hook) owns the form validation, the
 * optimistic "already reviewed" handling, and the per-row error UX.
 */
export function useCreateReview(): UseMutationResult<
  Review,
  unknown,
  ReviewCreateRequest
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ReviewCreateRequest) => createReview({ body }),
    onSuccess: () => {
      // Reviews: pick up the new review on the service-detail list (and any provider view).
      void queryClient.invalidateQueries({ queryKey: reviewKeys.all });
      // Bookings (customer side): a filed review is a booking lifecycle event.
      void queryClient.invalidateQueries({ queryKey: bookingKeys.all });
    },
  });
}
