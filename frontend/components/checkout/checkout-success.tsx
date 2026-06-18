"use client";

/**
 * Checkout success state — shown after Stripe confirms the PaymentIntent client-side.
 *
 * TRUST MODEL (important to understand this component):
 *   - The PRIMARY success signal is the Stripe CLIENT result
 *     (`stripe.confirmPayment` → paymentIntent.status === "succeeded"). That's what
 *     flipped the page to this view, and it's reliable: Stripe confirmed + captured
 *     the card. The user's card was charged.
 *   - The SECONDARY signal is the BACKEND's payment status, polled via
 *     GET /api/payments/order/{orderId}. The backend flips PENDING → SUCCEEDED only
 *     when the Stripe webhook lands. On the deployed app that's near-instant; in LOCAL
 *     DEV the webhook is NOT delivered unless Stripe CLI is forwarding, so the backend
 *     status may lag behind or never catch up.
 *
 * So we show "Payment successful" based on the Stripe client result, and surface the
 * backend status as secondary info ("server confirmation pending" if it hasn't caught
 * up). We never block the success UI on the backend status — that would make local dev
 * look broken when the payment actually succeeded.
 *
 * Visual (Phase 7): the success card is a green-tinted island; the backend status uses
 * the Badge primitive with tone variants instead of the inline color map. Links are
 * styled via buttonClasses so they read as clear CTAs.
 */
import Link from "next/link";
import type { PaymentIntent } from "@stripe/stripe-js";
import type { Payment, PaymentStatus } from "@/lib/api/payments";
import { Card } from "@/components/ui/card";
import { Badge, type BadgeTone } from "@/components/ui/badge";
import { buttonClasses } from "@/components/ui/button";

interface CheckoutSuccessProps {
  /** The confirmed PaymentIntent from the Stripe client. Source of the amount. */
  paymentIntent: PaymentIntent;
  /** Order id, shown as a reference. */
  orderId: number | undefined;
  /** Latest backend payment row, if the poller has one yet. May be undefined/erroring. */
  backendPayment?: Payment;
  /** True while the status query is still fetching (for the "checking…" affordance). */
  isPolling?: boolean;
}

/** Format a PaymentIntent amount (in cents) + currency code as a readable price. */
function formatAmount(amountCents: number, currency: string): string {
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      // Stripe uses lowercase ISO codes (e.g. "usd"); Intl wants uppercase ("USD").
      currency: (currency || "USD").toUpperCase(),
    }).format(amountCents / 100); // PaymentIntent.amount is in minor units (cents).
  } catch {
    // Unknown currency → show raw cents + code. Fallback so we never blank out.
    return `${(amountCents / 100).toFixed(2)} ${currency ?? ""}`.trim();
  }
}

/** Human-readable label for the BACKEND payment status pill. */
function describeBackendStatus(status: PaymentStatus | undefined): string {
  switch (status) {
    case "SUCCEEDED":
      return "Server confirmed ✓";
    case "PROCESSING":
      return "Server still processing…";
    case "FAILED":
      return "Server marked failed (Stripe said success — contact support if this persists)";
    case "PENDING":
    default:
      return "Server confirmation pending";
  }
}

/** Backend status → badge tone. Replaces the inline color map. */
function backendStatusTone(status: PaymentStatus | undefined): BadgeTone {
  switch (status) {
    case "SUCCEEDED":
      return "success";
    case "FAILED":
      return "danger";
    case "PROCESSING":
    case "PENDING":
    default:
      return "warning";
  }
}

export function CheckoutSuccess({
  paymentIntent,
  orderId,
  backendPayment,
  isPolling,
}: CheckoutSuccessProps) {
  const backendStatus = backendPayment?.status;

  return (
    <Card
      padded
      className="border-success/30 bg-success/10"
      data-testid="checkout-success"
      role="status"
    >
      <div className="flex items-start gap-3">
        <span className="text-2xl" aria-hidden="true">
          ✅
        </span>
        <div className="flex-1">
          <h2 className="text-lg font-semibold text-success">
            Payment successful
          </h2>
          <p className="mt-1 text-sm text-success/90">
            Charged{" "}
            <strong>
              {formatAmount(
                paymentIntent.amount ?? 0,
                paymentIntent.currency ?? "USD",
              )}
            </strong>{" "}
            to your card.
          </p>

          {/*
            Backend status — secondary. The webhook advances this; in local dev it may
            lag. We surface it as info, NOT as a blocker. "pending" is the expected
            local-dev state until Stripe CLI forwards the webhook.
          */}
          <div className="mt-4 flex flex-wrap items-center gap-2 text-xs">
            <Badge tone={backendStatusTone(backendStatus)}>
              {describeBackendStatus(backendStatus)}
            </Badge>
            {isPolling && backendStatus !== "SUCCEEDED" ? (
              <span className="text-success/80">checking server…</span>
            ) : null}
          </div>

          <dl className="mt-4 space-y-1 text-sm text-success/90">
            {orderId !== undefined ? (
              <div className="flex justify-between gap-4">
                <dt className="opacity-70">Order</dt>
                <dd className="font-medium">#{orderId}</dd>
              </div>
            ) : null}
            <div className="flex justify-between gap-4">
              <dt className="opacity-70">Stripe reference</dt>
              <dd className="font-mono text-xs">{paymentIntent.id ?? "—"}</dd>
            </div>
          </dl>

          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              href="/bookings"
              className={buttonClasses({ variant: "success", size: "sm" })}
            >
              Back to my bookings
            </Link>
            <Link
              href="/"
              className={buttonClasses({
                variant: "ghost",
                size: "sm",
                className: "text-success hover:bg-success/15",
              })}
            >
              Browse more services
            </Link>
          </div>
        </div>
      </div>
    </Card>
  );
}
