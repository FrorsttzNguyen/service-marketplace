"use client";

/**
 * Checkout page — the browse → book → PAY finish line.
 *
 * ROUTE: /checkout/[bookingId]  (Reached from "Pay now" on a CONFIRMED booking.)
 *
 * THE FLOW (in order):
 *   1. CREATE PAYMENT — POST /api/payments { bookingId, paymentMethod: "card" } →
 *      a PaymentResponse WITH clientSecret. The secret is what Stripe.js needs to
 *      mount <Elements> + <PaymentElement>.
 *   2. MOUNT STRIPE ELEMENTS — only AFTER we have the clientSecret. (Stripe requires
 *      the clientSecret at <Elements> mount time; it cannot be added/changed later.)
 *   3. CONFIRM — on submit, stripe.confirmPayment({ elements, redirect: "if_required"
 *      }) charges the card. On status "succeeded" we flip to the success UI.
 *   4. POLL BACKEND STATUS — GET /api/payments/booking/{bookingId} reflects the
 *      BACKEND's view, which the Stripe webhook advances. In local dev the webhook
 *      isn't delivered unless Stripe CLI forwards it, so the backend status may lag
 *      the client result. We show it as secondary ("server confirmation pending").
 *
 * ERROR / EDGE CASES:
 *   - Missing NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY → clear config message, no crash.
 *   - 422 on payment create (booking not confirmed/not yours) → tell the user.
 *   - 409 on payment create (payment already exists) → can't resume Elements (GET
 *     returns no clientSecret). Show "a payment already exists for this booking" +
 *     its backend status. Known v1 limitation; documented in lib/api/payments.ts.
 *   - Network/CORS → reuse <ErrorState> with the existing CORS hint.
 *
 * WHY THE REF: React 18 StrictMode double-invokes effects in dev. The create-payment
 * kickoff on mount is guarded by a ref so it fires exactly once even under the
 * double-invoke. In production the ref is a harmless no-op.
 *
 * Visual (Phase 7): the payment summary + payment form + status are each islands. The
 * Stripe Elements mount, status branches, and ref are UNCHANGED — only the wrapper
 * markup and Button styling changed.
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
import { useCreatePayment, usePaymentForBooking } from "@/lib/api/payments-queries";
import { paymentKeys } from "@/lib/api/payments-queries";
import { useQueryClient } from "@tanstack/react-query";
import { getStripe, getStripePublishableKey } from "@/lib/stripe/stripe-client";
import { Container } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { Button, buttonClasses } from "@/components/ui/button";
import type { Payment } from "@/lib/api/payments";

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
 * The actual checkout flow. Split out so the guards above (invalid id, missing key)
 * can short-circuit before any of the mutation machinery mounts.
 */
function CheckoutFlow({ bookingId }: { bookingId: number }) {
  // --- Mutations ----------------------------------------------------------
  const createPaymentMutation = useCreatePayment();
  const queryClient = useQueryClient();

  // --- Step state (each set once as we advance) ---------------------------
  // The payment returned by createPayment — carries amount/currency for the summary.
  const [payment, setPayment] = useState<Payment | null>(null);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  // Set when createPayment returns 409 — see KNOWN LIMITATION in lib/api/payments.ts.
  const [paymentAlreadyExists, setPaymentAlreadyExists] = useState(false);
  // The confirmed PaymentIntent from the Stripe CLIENT (success signal).
  const [confirmedPI, setConfirmedPI] = useState<PaymentIntent | null>(null);

  // Ref to make the create-payment kickoff idempotent under StrictMode double-invoke.
  const paymentStartedRef = useRef(false);

  // Local state for the create-payment error message (non-409). Kept separate from
  // the 409 flag so the two UIs read cleanly.
  const [paymentCreateMsg, setPaymentCreateMsg] = useState<string | null>(null);

  // STEP 1: create the payment directly on mount. Guarded by ONE ref so it fires
  // exactly once even under React 18 StrictMode double-invoke.
  useEffect(() => {
    if (paymentStartedRef.current) return; // already kicked off (StrictMode guard)
    paymentStartedRef.current = true;
    createPaymentMutation.mutate(
      { bookingId, paymentMethod: "card" },
      {
        onSuccess: (created) => {
          setPayment(created);
          // The ONLY call that returns a clientSecret. Feed it to <Elements>.
          if (created.clientSecret) {
            setClientSecret(created.clientSecret);
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
              queryKey: paymentKeys.byBooking(bookingId),
            });
          } else {
            setPaymentCreateMsg(describePaymentCreateError(err));
          }
        },
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bookingId]);

  // --- Bounded status poll (secondary success signal) ---------------------
  // Polls GET /api/payments/booking/{bookingId}. In local dev the webhook isn't
  // delivered, so this may stay PENDING — that's fine; we trust the Stripe client
  // result for the success UI and surface this status as secondary info.
  const statusQuery = usePaymentForBooking(bookingId);

  // --- RENDER BRANCHES ----------------------------------------------------

  // Payment-create failure (non-409 errors).
  if (paymentCreateMsg) {
    return (
      <CheckoutShell>
        <Card
          padded
          className="mt-6 border-danger/30 bg-danger/10 text-danger"
          role="alert"
        >
          <p className="font-semibold">Couldn&apos;t start the payment.</p>
          <p className="mt-1 text-sm">{paymentCreateMsg}</p>
          <Button
            variant="destructive"
            size="sm"
            className="mt-4"
            onClick={() => {
              // Allow a retry of the payment-create step.
              setPaymentCreateMsg(null);
              paymentStartedRef.current = true;
              createPaymentMutation.mutate(
                { bookingId, paymentMethod: "card" },
                {
                  onSuccess: (created) => {
                    setPayment(created);
                    if (created.clientSecret) {
                      setClientSecret(created.clientSecret);
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
            }}
          >
            Try again
          </Button>
        </Card>
      </CheckoutShell>
    );
  }

  // 409: a payment already exists for this booking. We can't remount Elements (no
  // clientSecret from GET). Show the existing payment + its status.
  if (paymentAlreadyExists) {
    return (
      <CheckoutShell>
        {payment ? <PaymentHeader payment={payment} /> : null}
        <ExistingPaymentState statusQuery={statusQuery} />
      </CheckoutShell>
    );
  }

  // SUCCESS: Stripe client confirmed the PaymentIntent. Trust this for the UI.
  if (confirmedPI) {
    return (
      <CheckoutShell>
        <CheckoutSuccess
          paymentIntent={confirmedPI}
          bookingId={bookingId}
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
        <div aria-busy="true">
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
      {payment ? <PaymentHeader payment={payment} /> : null}
      <Card padded className="mt-6">
        <h2 className="text-lg font-semibold text-foreground">Pay with card</h2>
        <p className="mt-1 text-sm text-muted-foreground">
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
                  borderRadius: "12px",
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
      </Card>

      {/*
        Secondary: backend status while the user is still on the form. Usually
        PENDING here. We only show it once we have data, to avoid a noisy 404 line
        before the payment is created.
      */}
      {statusQuery.data ? (
        <p className="mt-4 text-xs text-muted-foreground">
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
    <Container width="checkout">
      <p className="mb-6">
        <Link
          href="/bookings"
          className={buttonClasses({ variant: "ghost", size: "sm" })}
        >
          ← Back to my bookings
        </Link>
      </p>
      {children}
    </Container>
  );
}

/** Payment summary header (amount + currency from the PaymentResponse). */
function PaymentHeader({ payment }: { payment: Payment }) {
  const amount = formatPaymentAmount(payment.amount, payment.currency);
  return (
    <header>
      <h1 className="text-3xl font-bold tracking-tight text-foreground">Checkout</h1>
      <p className="mt-1.5 text-muted-foreground">
        Pay{" "}
        <strong className="text-foreground">{amount}</strong> for your booking.
      </p>
      {payment.bookingId !== undefined ? (
        <p className="mt-1 text-xs text-muted-foreground">Booking #{payment.bookingId}</p>
      ) : null}
    </header>
  );
}

/** Format a payment amount (backend uses major units, e.g. 12.5) + currency. */
function formatPaymentAmount(amount: number | undefined, currency: string | undefined): string {
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
    if (err.status === 404) return "The booking wasn't found.";
    if (err.status === 422)
      return "This booking isn't eligible for payment — it must be confirmed by the provider first, and not already paid.";
    return err.message;
  }
  return "Couldn't start the payment. Please try again.";
}

/** 409 state: a payment already exists; show its backend status, no resume UI. */
function ExistingPaymentState({
  statusQuery,
}: {
  statusQuery: ReturnType<typeof usePaymentForBooking>;
}) {
  const status = statusQuery.data?.status;
  return (
    <Card
      padded
      className="mt-6 border-warning/30 bg-warning/10 text-warning"
      role="status"
      data-testid="payment-already-exists"
    >
      <h2 className="text-lg font-semibold">
        A payment already exists for this booking
      </h2>
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
          This booking has already been paid.{" "}
          <Link href="/bookings" className="underline">
            Back to my bookings
          </Link>
        </p>
      ) : null}
      <p className="mt-3 text-xs opacity-80">
        Known v1 limitation: resuming an in-progress payment isn&apos;t supported
        yet. If you believe this is an error, contact support with your booking id.
      </p>
    </Card>
  );
}

/** Config-error state when the Stripe publishable key isn't set. */
function MissingStripeConfig() {
  return (
    <Card
      padded
      className="border-warning/30 bg-warning/10 text-warning"
      role="alert"
    >
      <h2 className="text-lg font-semibold">Payments aren&apos;t configured</h2>
      <p className="mt-1 text-sm">
        The app is missing the{" "}
        <code className="rounded-pill bg-warning/15 px-1.5 py-0.5">
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
          className="text-sm font-medium underline"
        >
          ← Back to my bookings
        </Link>
      </p>
    </Card>
  );
}
