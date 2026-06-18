"use client";

/**
 * TanStack Query hooks + mutations for orders.
 *
 * Query keys: every order query lives under the `["orders", ...]` family. Mutations
 * invalidate that family on success so any mounted order view stays fresh.
 *
 * Why a separate file from `bookings-queries.ts`: orders are a distinct resource
 * (created from bookings, but with their own lifecycle + the payment step downstream).
 * Keeping them isolated makes the checkout flow easier to follow. The pattern mirrors
 * `services.ts` + `queries.ts` and `bookings.ts` + `bookings-queries.ts`.
 */
import {
  useMutation,
  useQueryClient,
  type UseMutationResult,
} from "@tanstack/react-query";
import {
  createOrder,
  type Order,
  type OrderCreateRequest,
} from "./orders";

/** Centralized order query keys, so invalidation + keying stay in sync. */
export const orderKeys = {
  all: ["orders"] as const,
  lists: () => [...orderKeys.all, "list"] as const,
  details: () => [...orderKeys.all, "detail"] as const,
  detail: (id: number) => [...orderKeys.details(), { id }] as const,
} as const;

/**
 * Create an order from a confirmed booking.
 *
 * The backend is idempotent on bookingId (see `createOrder` in orders.ts), so this
 * mutation is SAFE to fire multiple times for the same booking — e.g. on a checkout
 * page reload. The first call creates the order; subsequent calls return the same
 * payable order, and we proceed to the payment step either way.
 *
 * On success, invalidates the `["orders", ...]` family (cheap — there's no order
 * list in the UI yet, but reserved for future use). The checkout page reads the
 * returned order directly from the mutation result to drive the next step.
 */
export function useCreateOrder(): UseMutationResult<
  Order,
  unknown,
  OrderCreateRequest
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: OrderCreateRequest) => createOrder(body),
    onSuccess: (order) => {
      // Pre-seed the detail cache for this order id so a follow-up useGetOrder doesn't
      // refetch what we just received. invalidateQueries keeps the family consistent.
      if (order.id !== undefined) {
        void queryClient.invalidateQueries({ queryKey: orderKeys.all });
      }
    },
  });
}
