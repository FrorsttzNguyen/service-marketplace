"use client";

/**
 * Checkout page — the browse → book → PAY finish line.
 *
 * ROUTE: /checkout/[bookingId]  (Reached from "Pay now" on a CONFIRMED booking.)
 *
 * THE FLOW (in order):
 *   1. CREATE ORDER — POST /api/orders { bookingId } → orderId.
 *      Idempotent, so a page reload re-creates the SAME order (no duplicate). Safe.
 *   2. CREATE PAYMENT — POST /api/payments { orderId, paymentMethod: "card" } →
 *      a PaymentResponse WITH clientSecret. The secret is what Stripe.js needs to
 *      mount <Elements> + <PaymentElement>.
 *   3. MOUNT STRIPE ELEMENTS — only AFTER we have the clientSecret. (Stripe requires
 *      the clientSecret at <Elements> mount time; it cannot be added/changed later.)
 *   4. CONFIRM — on submit, stripe.confirmPayment({ elements, redirect: "if_required"
 *      }) charges the card. On status "succeeded" we flip to the success UI.
 *   5. POLL BACKEND STATUS — GET /api/payments/order/{orderId} reflects the BACKEND's
 *      view, which the Stripe webhook advances. In local dev the webhook isn't
 *      delivered unless Stripe CLI forwards it, so the backend status may lag the
 *      client result. We show it as secondary ("server confirmation pending").
 *
 * ERROR / EDGE CASES:
 *   - Missing NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY → clear config message, no crash.
 *   - 422 on order create (booking not confirmed/not yours) → tell the user.
 *   - 409 on payment create (payment already exists) → can't resume Elements (GET
 *     returns no clientSecret). Show "a payment already exists" + its backend status.
 *     Known v1 limitation; documented in lib/api/payments.ts.
 *   - Network/CORS → reuse <ErrorState> with the existing CORS hint.
 *
 * WHY THE REFS: React 18 StrictMode double-invokes effects in dev. Each kickoff
 * (create-order on mount, create-payment once orderId is known) is guarded by a ref
 * so it fires exactly once even under the double-invoke. In production the refs are a
 * harmless no-op.
 */
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Elements } from "@stripe/react-stripe-js";
import type { PaymentIntent } from "@stripe/stripe-js";
import { RequireAuth } from "@/components/require-auth";
import { ErrorState } from "@/components/error-state";
import { ServiceDetailSkeleton } from "@/components/skeletons";
import { CheckoutForm } from "@/components/checkout/checkout-form";
import { CheckoutSuccess } from "@/components/checkout/checkout-success";
import { ApiError } from "@/lib/api/client";
import { useCreateOrder } from "@/lib/api/orders-queries";
import type { Order } from "@/lib/api/orders";
import { useCreatePayment, usePaymentForOrder } from "@/lib/api/payments-queries";
import { useQueryClient } from "@tanstack/react-query";
import { paymentKeys } from "@/lib/api/payments-queries";
import { getStripe, getStripePublishableKey } from "@/lib/stripe/stripe-client";

export default function CheckoutPage() {
  return (
    <RequireAuth>
      <CheckoutContent />
    </RequireAuth>
  );
}

function CheckoutContent() {
  const params = useParams<{ bookingId: string }>();
  const rawBookingId = params?.bookingId;

  // Parse the route param. A non-numeric id can't reference a booking — show not-found
  // instead of firing doomed requests. Mirrors the detail-page id handling.
  const bookingId = rawBookingId !== undefined ? Number(rawBookingId) : NaN;
  const isValidBookingId = Number.isInteger(bookingId) && bookingId > 0;

  // Config check FIRST: if there's no publishable key, we can't mount Stripe at all.
  // Resolve once on mount; it never changes at runtime (NEXT_PUBLIC_ is build-time).
  const stripeKey = getStripePublishableKey();

  if (!isValidBookingId) {
    return (
      <CheckoutShell>
        <ErrorState
          error={null}
          notFound
          title="Invalid checkout link."
          hint={
            <span>
              The booking id in the URL isn&apos;t valid.{" "}
              <Link href="/bookings" className="underline">
                Back to my bookings
              </Link>
            </span>
          }
        />
      </CheckoutShell>
    );
  }

  if (!stripeKey) {
    return (
      <CheckoutShell>
        <MissingStripeConfig />
      </CheckoutShell>
    );
  }

  return <CheckoutFlow bookingId={bookingId} />;
}

/**
 * The actual ordered checkout flow. Split out so the guards above (invalid id, missing
 * key) can short-circuit before any of the mutation machinery mounts.
 */
function CheckoutFlow({ bookingId }: { bookingId: number }) {
  // --- Mutations ----------------------------------------------------------
  const createOrderMutation = useCreateOrder();
  const createPaymentMutation = useCreatePayment();
  const queryClient = useQueryClient();

  // --- Step state (each set once as we advance) ---------------------------
  const [order, setOrder] = useState<Order | null>(null);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  // Set when createPayment returns 409 — see KNOWN LIMITATION in lib/api/payments.ts.
  const [paymentAlreadyExists, setPaymentAlreadyExists] = useState(false);
  // The confirmed PaymentIntent from the Stripe CLIENT (success signal).
  const [confirmedPI, setConfirmedPI] = useState<PaymentIntent | null>(null);

  // Refs to make each kickoff idempotent under StrictMode double-invoke.
  const orderStartedRef = useRef(false);
  const paymentStartedRef = useRef(false);

  // STEP 1: create the order on mount. Idempotent, so a reload just re-returns it.
  useEffect(() => {
    if (orderStartedRef.current) return; // already kicked off (StrictMode guard)
    orderStartedRef.current = true;
    createOrderMutation.mutate(
      { bookingId },
      {
        onSuccess: (created) => {
          setOrder(created);
        },
        // Errors handled below via createOrderMutation.error; nothing to do here.
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bookingId]);

  // STEP 2: once we have an order id, create the payment → clientSecret.
  useEffect(() => {
    const orderId = order?.id;
    if (orderId === undefined) return; // order not ready yet
    if (paymentStartedRef.current) return; // already kicked off
    paymentStartedRef.current = true;

    createPaymentMutation.mutate(
      { orderId, paymentMethod: "card" },
      {
        onSuccess: (payment) => {
          // The ONLY call that returns a clientSecret. Feed it to <Elements>.
          if (payment.clientSecret) {
            setClientSecret(payment.clientSecret);
          } else {
            // Defensive: backend returned 201 but no secret. Treat as a hard error.
            setPaymentCreateMsg(
              "The server didn't return a payment secret. Please try again.",
            );
          }
        },
        onError: (err: unknown) => {
          if (err instanceof ApiError && err.status === 409) {
            // 409 → a payment already exists. We can't resume Elements (GET returns
            // no secret). Show the existing-payment state + its backend status.
            setPaymentAlreadyExists(true);
            // Make the status query refetch so we can show the existing payment's
            // current state (it may have been 404'ing before this payment existed).
            void queryClient.invalidateQueries({
              queryKey: paymentKeys.byOrder(orderId),
            });
          } else {
            setPaymentCreateMsg(describePaymentCreateError(err));
          }
        },
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order?.id]);

  // --- Bounded status poll (secondary success signal) ---------------------
  // Polls GET /api/payments/order/{orderId}. In local dev the webhook isn't delivered,
  // so this may stay PENDING — that's fine; we trust the Stripe client result for the
  // success UI and surface this status as secondary info.
  const orderIdForPoll = order?.id ?? 0;
  const statusQuery = usePaymentForOrder(orderIdForPoll);

  // Local state for the create-payment error message (non-409). Kept separate from
  // the 409 flag so the two UIs read cleanly.
  const [paymentCreateMsg, setPaymentCreateMsg] = useState<string | null>(null);

  // --- RENDER BRANCHES ----------------------------------------------------

  // Order-create failure (404 booking gone, 422 not confirmed / not owner, network).
  if (createOrderMutation.isError) {
    return (
      <CheckoutShell>
        <ErrorState
          error={createOrderMutation.error}
          title="Couldn't start checkout."
          hint={
            createOrderMutation.error instanceof ApiError &&
            createOrderMutation.error.status === 422 ? (
              <span>
                This booking can&apos;t be paid yet — it must be confirmed by the
                provider first.{" "}
                <Link href="/bookings" className="underline">
                  Back to my bookings
                </Link>
              </span>
            ) : null
          }
          onRetry={() => {
            // Allow a manual retry of the order-create step.
            orderStartedRef.current = false;
            createOrderMutation.mutate(
              { bookingId },
              { onSuccess: (created) => setOrder(created) },
            );
          }}
        />
      </CheckoutShell>
    );
  }

  // Order still creating → loading.
  if (!order) {
    return (
      <CheckoutShell>
        <ServiceDetailSkeleton />
      </CheckoutShell>
    );
  }

  // 409: a payment already exists for this order. We can't remount Elements (no
  // clientSecret from GET). Show the existing payment + its status.
  if (paymentAlreadyExists) {
    return (
      <CheckoutShell>
        <OrderHeader order={order} />
        <ExistingPaymentState statusQuery={statusQuery} />
      </CheckoutShell>
    );
  }

  // Non-409 payment-create error.
  if (paymentCreateMsg) {
    return (
      <CheckoutShell>
        <OrderHeader order={order} />
        <div
          className="mt-6 rounded border border-red-300 bg-red-50 p-6 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
          role="alert"
        >
          <p className="font-semibold">Couldn&apos;t start the payment.</p>
          <p className="mt-1 text-sm">{paymentCreateMsg}</p>
          <button
            type="button"
            className="mt-4 rounded bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
            onClick={() => {
              // Allow a retry of the payment-create step only.
              setPaymentCreateMsg(null);
              paymentStartedRef.current = false;
              // Re-trigger by toggling the effect: easiest is to call mutate directly.
              if (order.id !== undefined) {
                paymentStartedRef.current = true;
                createPaymentMutation.mutate(
                  { orderId: order.id, paymentMethod: "card" },
                  {
                    onSuccess: (payment) => {
                      if (payment.clientSecret) {
                        setClientSecret(payment.clientSecret);
                      }
                    },
                    onError: (err: unknown) => {
                      if (err instanceof ApiError && err.status === 409) {
                        setPaymentAlreadyExists(true);
                      } else {
                        setPaymentCreateMsg(describePaymentCreateError(err));
                      }
                    },
                  },
                );
              }
            }}
          >
            Try again
          </button>
        </div>
      </CheckoutShell>
    );
  }

  // SUCCESS: Stripe client confirmed the PaymentIntent. Trust this for the UI.
  if (confirmedPI) {
    return (
      <CheckoutShell>
        <CheckoutSuccess
          paymentIntent={confirmedPI}
          orderId={order.id}
          backendPayment={statusQuery.data}
          isPolling={statusQuery.isFetching}
        />
      </CheckoutShell>
    );
  }

  // Waiting for the clientSecret (payment create in flight).
  if (!clientSecret) {
    return (
      <CheckoutShell>
        <OrderHeader order={order} />
        <div className="mt-6" aria-busy="true">
          <ServiceDetailSkeleton />
        </div>
      </CheckoutShell>
    );
  }

  // MAIN STATE: mount Stripe Elements with the clientSecret and render the form.
  // Elements is memoized via getStripe(); options.clientSecret is set ONCE here —
  // Stripe does not allow changing it after mount, which is why we render <Elements>
  // only after clientSecret is non-null (conditional mount).
  return (
    <CheckoutShell>
      <OrderHeader order={order} />
      <div className="mt-6 rounded-lg border border-neutral-200 p-6 dark:border-neutral-800">
        <h2 className="text-lg font-semibold">Pay with card</h2>
        <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
          Secure payment via Stripe.
        </p>

        {/*
          Elements provides the Stripe context. The form (and its useStripe/
          useElements hooks) MUST be a descendant. appearance keeps the Stripe iframe
          visually consistent with the app's rounded inputs.
        */}
        <div className="mt-4">
          <Elements
            stripe={getStripe()}
            options={{
              clientSecret,
              appearance: {
                theme: "stripe",
                variables: {
                  borderRadius: "6px",
                },
              },
            }}
          >
            <CheckoutForm
              onSuccess={(pi) => setConfirmedPI(pi)}
              onError={() => {
                /* error already shown inline by the form; nothing else needed */
              }}
            />
          </Elements>
        </div>
      </div>

      {/*
        Secondary: backend status while the user is still on the form. Usually
        PENDING here. We only show it once we have data, to avoid a noisy 404 line
        before the payment is created.
      */}
      {statusQuery.data ? (
        <p className="mt-4 text-xs text-neutral-500 dark:text-neutral-400">
          Server payment status:{" "}
          <span className="font-mono">{statusQuery.data.status ?? "—"}</span>
        </p>
      ) : null}
    </CheckoutShell>
  );
}

// --- Helper subcomponents --------------------------------------------------

/** Page chrome: max-width container + back link to bookings. */
function CheckoutShell({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <p className="mb-6">
        <Link
          href="/bookings"
          className="text-sm text-blue-600 hover:underline dark:text-blue-400"
        >
          ← Back to my bookings
        </Link>
      </p>
      {children}
    </main>
  );
}

/** Order summary header (amount + currency + reference). */
function OrderHeader({ order }: { order: Order }) {
  const amount = formatOrderAmount(order.totalAmount, order.currency);
  return (
    <header>
      <h1 className="text-3xl font-bold tracking-tight">Checkout</h1>
      <p className="mt-1 text-neutral-600 dark:text-neutral-400">
        Pay{" "}
        <strong className="text-neutral-900 dark:text-neutral-100">
          {amount}
        </strong>{" "}
        for your booking.
      </p>
      {order.id !== undefined ? (
        <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
          Order #{order.id}
        </p>
      ) : null}
    </header>
  );
}

/** Format an order amount (backend uses major units, e.g. 12.5) + currency. */
function formatOrderAmount(amount: number | undefined, currency: string | undefined): string {
  if (amount === undefined || amount === null) return "—";
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: (currency || "USD").toUpperCase(),
    }).format(amount);
  } catch {
    return `${amount} ${currency ?? ""}`.trim();
  }
}

/** Map a non-409 payment-create error to a user-facing line. */
function describePaymentCreateError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 404) return "The order for this booking wasn't found.";
    if (err.status === 422)
      return "This order isn't eligible for payment (it may already be paid).";
    return err.message;
  }
  return "Couldn't start the payment. Please try again.";
}

/** 409 state: a payment already exists; show its backend status, no resume UI. */
function ExistingPaymentState({
  statusQuery,
}: {
  statusQuery: ReturnType<typeof usePaymentForOrder>;
}) {
  const status = statusQuery.data?.status;
  return (
    <div
      className="mt-6 rounded border border-amber-300 bg-amber-50 p-6 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
      role="status"
      data-testid="payment-already-exists"
    >
      <h2 className="text-lg font-semibold">A payment already exists for this order</h2>
      <p className="mt-1 text-sm">
        We can&apos;t reopen the card form for an existing payment in this version.
        Its current status is{" "}
        <span className="font-mono font-medium">
          {status ?? "loading…"}
        </span>
        .
      </p>
      {status === "SUCCEEDED" ? (
        <p className="mt-2 text-sm">
          This order has already been paid.{" "}
          <Link href="/bookings" className="underline">
            Back to my bookings
          </Link>
        </p>
      ) : null}
      <p className="mt-3 text-xs opacity-80">
        Known v1 limitation: resuming an in-progress payment isn&apos;t supported
        yet. If you believe this is an error, contact support with your order id.
      </p>
    </div>
  );
}

/** Config-error state when the Stripe publishable key isn't set. */
function MissingStripeConfig() {
  return (
    <div
      className="rounded border border-amber-300 bg-amber-50 p-6 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
      role="alert"
    >
      <h2 className="text-lg font-semibold">Payments aren&apos;t configured</h2>
      <p className="mt-1 text-sm">
        The app is missing the{" "}
        <code className="rounded bg-amber-100 px-1 dark:bg-amber-900/60">
          NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
        </code>{" "}
        environment variable, so the checkout form can&apos;t load.
      </p>
      <p className="mt-2 text-xs opacity-80">
        Set a Stripe test publishable key (starts with{" "}
        <code>pk_test_</code>) in{" "}
        <code>.env.local</code> for local dev, or in the Vercel project settings for
        production, then restart the dev server.
      </p>
      <p className="mt-3">
        <Link
          href="/bookings"
          className="text-sm font-medium text-amber-800 underline dark:text-amber-300"
        >
          ← Back to my bookings
        </Link>
      </p>
    </div>
  );
}
