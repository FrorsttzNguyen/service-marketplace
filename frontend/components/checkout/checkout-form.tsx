"use client";

/**
 * Checkout form — the Stripe <PaymentElement> + the "Pay" button.
 *
 * MUST be rendered inside <Elements> (the Stripe context provider). The two hooks we
 * use — `useStripe()` and `useElements()` — only work within that provider; outside it
 * they return null. The checkout page owns the <Elements> wrapper and mounts this
 * component inside it once it has a clientSecret.
 *
 * CONFIRM FLOW:
 *   1. stripe.confirmPayment({ elements, redirect: "if_required" }) submits the card
 *      details directly to Stripe, which confirms the PaymentIntent server-side at
 *      Stripe. `redirect: "if_required"` keeps the user ON THIS PAGE for card payments
 *      (cards don't need a redirect). Some non-card methods (e.g. iDEAL) WOULD
 *      redirect — but v1 only supports cards, so we stay on-page.
 *   2. On success (result.paymentIntent.status === "succeeded"), we lift the result up
 *      via onSuccess so the page can show the success state + the amount/order id.
 *   3. On a Stripe-side error (decline, network to Stripe, etc.), we show the error
 *      message inline and let the user retry (the PaymentElement stays mounted).
 *
 * NOTE: a succeeded client-side confirm does NOT mean the BACKEND knows yet — the
 * backend's payment status is advanced by the Stripe webhook, which in local dev is
 * not delivered unless Stripe CLI is forwarding. The page handles that by polling the
 * backend status separately (see app/checkout/[bookingId]/page.tsx). This component
 * only reports the Stripe client result; the success UI trusts it.
 *
 * TEST CARD: 4242 4242 4242 4242, any future expiry, any CVC, any ZIP.
 *
 * Visual (Phase 7): the Stripe iframe container is a soft rounded panel that matches
 * the Input primitive's border radius; the Pay button + the error box use the Button /
 * danger-tint styling. Stripe mount + confirm logic UNCHANGED.
 */
import { useState, type FormEvent } from "react";
import {
  PaymentElement,
  useElements,
  useStripe,
} from "@stripe/react-stripe-js";
import type { PaymentIntent } from "@stripe/stripe-js";
import { Button } from "@/components/ui/button";

interface CheckoutFormProps {
  /**
   * Called when Stripe confirms the PaymentIntent client-side with status
   * "succeeded". The page uses this to flip to the success UI. We pass the full
   * PaymentIntent so the page can read amount/currency/id.
   */
  onSuccess: (paymentIntent: PaymentIntent) => void;
  /**
   * Called when confirmPayment returns a Stripe error (decline, network, etc.).
   * The form ALSO renders the error inline; this callback lets the page log/track it.
   */
  onError?: (message: string) => void;
}

export function CheckoutForm({ onSuccess, onError }: CheckoutFormProps) {
  // These are null until <Elements> finishes initializing with the clientSecret.
  // We must guard every usage — calling .confirmPayment on null would crash.
  const stripe = useStripe();
  const elements = useElements();

  const [submitting, setSubmitting] = useState(false);
  // Stripe-side error message (decline, network-to-Stripe, etc.). Empty = no error.
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Elements not ready yet (stripe.js still loading or clientSecret not applied).
  // We disable the button rather than hiding the form so the layout is stable.
  const notReady = !stripe || !elements;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setErrorMessage(null);

    // Belt-and-suspenders: the button is disabled when notReady, but a fast double
    // submit could still land here before stripe loads. Guard explicitly.
    if (!stripe || !elements) {
      return;
    }

    setSubmitting(true);

    // confirmPayment sends the card data straight to Stripe (not our backend) and
    // confirms the PaymentIntent. `redirect: "if_required"` means: for cards, stay
    // here and return the result; for redirect-based methods, follow the redirect.
    const result = await stripe.confirmPayment({
      elements,
      redirect: "if_required",
    });

    // Stripe returns either { paymentIntent } on success or { error } on failure —
    // never both, never neither (for the card path). We branch accordingly.
    if (result.error) {
      // Most common: card_declined. result.error.message is user-facing (Stripe
      // localizes it). Show inline + lift up so the page can react.
      const msg = result.error.message ?? "Payment failed. Please try again.";
      setErrorMessage(msg);
      onError?.(msg);
      setSubmitting(false);
      return;
    }

    // Success path: the PaymentIntent is confirmed. status "succeeded" = captured
    // (card, no extra auth). "processing" / "requires_action" would need extra
    // handling for 3DS / async methods — not needed for v1 test cards.
    if (result.paymentIntent && result.paymentIntent.status === "succeeded") {
      onSuccess(result.paymentIntent);
      // NOTE: we deliberately do NOT reset `submitting` — leaving the form in a
      // disabled state post-success prevents a second confirm while the page flips
      // to the success view.
      return;
    }

    // Non-succeeded, non-error status (e.g. "processing" for an async method). v1
    // only supports cards, so this shouldn't happen with the test card, but we handle
    // it defensively instead of leaving the spinner spinning forever.
    setErrorMessage(
      "Payment is still processing. Check your bookings in a moment.",
    );
    onError?.("Payment entered an unexpected processing state.");
    setSubmitting(false);
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/*
        PaymentElement renders the card (and, if enabled, other method) input fields.
        It's a Stripe-hosted iframe — the card data never touches our JS, which is the
        core of Stripe's PCI compliance story. We just style the container.
      */}
      <div className="rounded-2xl border border-border bg-muted/40 p-3">
        <PaymentElement
          // Show card brand icons + accessible labels; Stripe defaults are fine.
          options={{ layout: { type: "tabs", defaultCollapsed: false } }}
        />
      </div>

      {errorMessage ? (
        <div
          className="rounded-2xl border border-danger/30 bg-danger/10 p-3 text-sm text-danger"
          role="alert"
        >
          {errorMessage}
        </div>
      ) : null}

      <Button
        type="submit"
        size="lg"
        fullWidth
        disabled={notReady || submitting}
        isLoading={submitting}
      >
        {/* When submitting, the spinner shows next to this label ("Processing…").
            notReady (no spinner) shows "Loading…" while Stripe.js initializes. */}
        {notReady && !submitting ? "Loading…" : submitting ? "Processing…" : "Pay"}
      </Button>

      <p className="text-center text-xs text-muted-foreground">
        Test mode — use{" "}
        <code className="rounded-pill bg-muted px-1.5 py-0.5">
          4242 4242 4242 4242
        </code>
        , any future expiry, any CVC, any ZIP.
      </p>
    </form>
  );
}
