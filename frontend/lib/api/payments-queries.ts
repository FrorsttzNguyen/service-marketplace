"use client";

/**
 * TanStack Query hooks + mutations for payments.
 *
 * Two concerns live here:
 *
 * 1. `useCreatePayment` — a mutation that mints a Stripe PaymentIntent and returns
 *    the clientSecret. Fires exactly once per checkout session (the checkout page
 *    holds the result in component state and feeds the secret to <Elements>).
 *
 * 2. `usePaymentForBooking` — a QUERY that polls GET /api/payments/booking/{bookingId}
 *    to reflect the BACKEND's view of the payment status. The backend flips PENDING →
 *    SUCCEEDED only when the Stripe webhook lands. POLLING IS BOUNDED — see the
 *    in-component comment — because in local dev the webhook is NOT delivered unless
 *    Stripe CLI is forwarding, so the backend status can stay PENDING/PROCESSING
 *    forever even after the card confirms client-side. We poll a few times then stop
 *    and let the Stripe client result drive the success UI.
 *
 * Query keys live under the `["payments", ...]` family. The create-payment mutation
 * invalidates that family on success so the status query (if already mounted) refetches
 * with the freshly-created payment instead of 404-ing.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  createPayment,
  getPaymentForBooking,
  type Payment,
  type SupportedPaymentMethod,
} from "./payments";

/** Centralized payment query keys, so invalidation + keying stay in sync. */
export const paymentKeys = {
  all: ["payments"] as const,
  details: () => [...paymentKeys.all, "detail"] as const,
  /** Key for the booking-scoped status query (used by the bounded poller). */
  byBooking: (bookingId: number) =>
    [...paymentKeys.details(), "byBooking", { bookingId }] as const,
} as const;

/** Params for the create-payment mutation. */
export interface UseCreatePaymentArgs {
  bookingId: number;
  /** v1 supports "card" only; defaults to "card" when omitted. */
  paymentMethod?: SupportedPaymentMethod;
}

/**
 * Create a payment (mint a Stripe PaymentIntent + clientSecret).
 *
 * Fires once per checkout session. On success, invalidates the payments family so a
 * mounted status query refetches (otherwise it might still hold a 404 from before
 * the payment existed). The checkout page reads the returned Payment (with its
 * clientSecret) directly from the mutation result to mount Stripe <Elements>.
 */
export function useCreatePayment(): UseMutationResult<
  Payment,
  unknown,
  UseCreatePaymentArgs
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (args: UseCreatePaymentArgs) =>
      createPayment({
        bookingId: args.bookingId,
        paymentMethod: args.paymentMethod ?? "card",
      }),
    onSuccess: () => {
      // The payment now exists — a 404'd status query should retry against the new row.
      void queryClient.invalidateQueries({ queryKey: paymentKeys.all });
    },
  });
}

/**
 * Polling budget for the status query, in number of refetches.
 *
 * WHY BOUNDED: the backend payment status is advanced by the Stripe webhook
 * (/api/webhooks/stripe). In local dev that webhook is NOT delivered unless Stripe
 * CLI is forwarding it to localhost, so the backend status can stay PENDING/
 * PROCESSING indefinitely even after the card confirms client-side. Polling forever
 * would (a) hammer a free-tier backend and (b) never resolve. Instead we poll a
 * bounded number of times (≈ 2.5 min at the interval below), then stop and surface
 * the last-known status as "server confirmation pending" — the Stripe client result
 * is the source of truth for the success UI either way.
 *
 * 15 refetches × ~10s interval ≈ 2.5 min of polling. Tunable.
 */
const STATUS_POLL_MAX_RETRIES = 15;

/**
 * Refetch interval for the status query while polling.
 *
 * ~10s is a reasonable trade-off: fast enough to catch a webhook that lands promptly
 * (e.g. on the deployed app, where the webhook IS delivered), slow enough to not
 * spam a cold-starting free-tier backend. Paired with STATUS_POLL_MAX_RETRIES above.
 */
const STATUS_POLL_INTERVAL_MS = 10_000;

/**
 * Poll the backend's view of a payment's status
 * (`GET /api/payments/booking/{bookingId}`).
 *
 * `enabled: bookingId > 0` — only start polling once we have a valid booking id.
 * Returns the standard query result so the checkout page can branch on data/error
 * and read the latest status.
 *
 * NOTE on refetchInterval being a function: returning `false` once the payment has
 * reached a terminal status (SUCCEEDED / FAILED) STOPS the poller early, so we don't
 * keep asking about a payment that's already done. The hard cap
 * (STATUS_POLL_MAX_RETRIES) is the backstop for the local-dev case where the status
 * never flips (webhook not delivered).
 *
 * We DON'T retry on error: a 404 just means "no payment yet" (pre-createPayment), and
 * a 403/500 isn't going to fix itself on retry. The default 1 retry from Providers is
 * fine; we keep this explicit for clarity.
 */
export function usePaymentForBooking(
  bookingId: number,
): UseQueryResult<Payment> {
  return useQuery({
    queryKey: paymentKeys.byBooking(bookingId),
    queryFn: ({ signal }) => getPaymentForBooking({ bookingId }),
    enabled: bookingId > 0,
    // Bounded polling: stop early on terminal status, AND hard-cap the number of
    // successful (200) polls. NOTE: `retry` only bounds consecutive ERRORS — it does
    // NOT bound interval-driven refetches of a 200 response. In local dev the webhook
    // isn't delivered, so GET keeps returning 200 with status PENDING; without the
    // dataUpdateCount cap below this would poll every interval FOREVER. dataUpdateCount
    // increments on each successful fetch, so it's the right counter to cap on.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === "SUCCEEDED" || status === "FAILED") {
        return false; // terminal — stop polling
      }
      if (query.state.dataUpdateCount >= STATUS_POLL_MAX_RETRIES) {
        return false; // hard cap reached (local-dev webhook gap) — give up
      }
      return STATUS_POLL_INTERVAL_MS;
    },
    // Errors (e.g. a transient 404 before the payment row exists) get a couple of
    // retries only; the interval cap above is what bounds the steady-state polling.
    retry: 2,
    // Don't treat a transient 404 (payment not created yet) as a hard error in the UI —
    // the checkout page only shows the status as secondary info anyway.
    staleTime: 0,
  });
}
