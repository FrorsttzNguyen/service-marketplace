"use client";

/**
 * TanStack Query hooks + mutations for bookings.
 *
 * Query keys: every booking query lives under the `["bookings", ...]` family. Both
 * mutations invalidate that whole family on success so the "My bookings" list refreshes
 * after a create/cancel without each call site doing it manually.
 *
 * Why a separate file from catalog `queries.ts`: bookings are a distinct resource area
 * with their own auth requirement and lifecycle; keeping them isolated makes Slice 4
 * (payment) and future slices easier to follow. The pattern mirrors `services.ts` +
 * `queries.ts`.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  cancelBooking,
  createBooking,
  listMyBookings,
  type Booking,
  type BookingCreateRequest,
  type BookingPage,
} from "./bookings";

/** Centralized booking query keys, so invalidation + keying stay in sync. */
export const bookingKeys = {
  all: ["bookings"] as const,
  lists: () => [...bookingKeys.all, "list"] as const,
  list: (page: number, size: number) =>
    [...bookingKeys.lists(), { page, size }] as const,
} as const;

/** Pagination options for the "my bookings" list. */
export interface UseMyBookingsOptions {
  page?: number;
  size?: number;
}

/**
 * Fetch the current user's bookings (paginated). Returns the standard query result so
 * callers can branch on isPending/isError/data. The endpoint is JWT-scoped server-side.
 */
export function useMyBookings(
  options: UseMyBookingsOptions = {},
): UseQueryResult<BookingPage> {
  const { page = 0, size = 10 } = options;
  return useQuery({
    queryKey: bookingKeys.list(page, size),
    queryFn: ({ signal }) => listMyBookings({ page, size }),
    staleTime: 30_000,
  });
}

/**
 * Create a booking. On success, invalidates all `["bookings", ...]` queries so the
 * list reflects the new booking. The page (not the hook) handles redirect + error UX.
 */
export function useCreateBooking(): UseMutationResult<
  Booking,
  unknown,
  BookingCreateRequest
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: BookingCreateRequest) => createBooking(body),
    onSuccess: () => {
      // Broad invalidation: a new booking changes every page of the list.
      void queryClient.invalidateQueries({ queryKey: bookingKeys.all });
    },
  });
}

/**
 * Cancel a booking by id (PUT /api/bookings/{id}/cancel). On success, invalidates the
 * bookings family so the list re-fetches with the new CANCELLED status. The page is
 * responsible for the PENDING-only gate + the 422 "status changed" handling.
 */
export function useCancelBooking(): UseMutationResult<
  Booking,
  unknown,
  number // booking id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => cancelBooking({ id }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: bookingKeys.all });
    },
  });
}
