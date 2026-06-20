"use client";

/**
 * Booking row — one entry in the "My bookings" list.
 *
 * Shows the service title, provider, time window (formatted), status badge, price, and
 * quantity. Status-gated actions:
 *   - Cancel: PENDING only (the backend rejects cancel for any other status with 422).
 *     Clicking it calls the cancel mutation the parent wires up. A 422 (status changed
 *     under us) surfaces the server message and the parent refetches.
 *   - Pay now: CONFIRMED only. A booking must be CONFIRMED before an order can be
 *     required before payment (the backend rejects POST /api/payments with 422 otherwise). The
 *     button is a plain link to /checkout/<bookingId> — the checkout page handles the
 *     order → payment → Stripe flow.
 *   - Leave a review: COMPLETED only. Renders the inline <ReviewForm>, which POSTs to
 *     /api/reviews. A review is the terminal step of the booking lifecycle and can only
 *     be filed once per booking; the form treats a 422 "already reviewed" as success.
 *
 * Visual (Phase 7): each row is an island (Card). Status uses the BookingStatusBadge
 * primitive (replaces the inline statusBadgeClass map). Actions use the Button
 * primitive; "Pay now" is a real <Link> styled via buttonClasses so navigation stays a
 * link.
 */
import Link from "next/link";
import type { Booking, BookingStatus } from "@/lib/api/bookings";
import { ReviewForm } from "@/components/review-form";
import { Card } from "@/components/ui/card";
import { Button, buttonClasses } from "@/components/ui/button";
import { BookingStatusBadge } from "@/components/ui/badge";

interface BookingCardProps {
  booking: Booking;
  /** True while a cancel mutation is in flight for THIS booking (disables the button). */
  isCancelling?: boolean;
  /** Called when the user clicks Cancel. Parent decides confirm + mutation. */
  onCancel?: (id: number) => void;
  /** Error message from a failed cancel for THIS booking (e.g. the 422 message). */
  cancelError?: string | null;
}

/** Format a date-time ISO string as a readable local time, or "—" if missing/invalid. */
function formatDateTime(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/** Format a price + currency code (e.g. 12.5 + "USD" → "$12.50"). */
function formatPrice(amount: number | undefined, currency: string | undefined): string {
  if (amount === undefined || amount === null) return "—";
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: currency || "USD",
    }).format(amount);
  } catch {
    // Unknown currency code → show raw amount.
    return `${amount} ${currency ?? ""}`.trim();
  }
}

/** Small dt/dd pair used in the meta grid. */
function MetaCell({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </dt>
      <dd className="mt-0.5 text-sm text-foreground">{children}</dd>
    </div>
  );
}

export function BookingCard({
  booking,
  isCancelling = false,
  onCancel,
  cancelError = null,
}: BookingCardProps) {
  const status: BookingStatus = booking.status ?? "PENDING";
  const canCancel = status === "PENDING" && onCancel !== undefined;
  // Pay-now is gated to CONFIRMED: only confirmed bookings can be paid directly. The
  // checkout page will also enforce this server-side (422), but gating here avoids a
  // pointless navigation for PENDING/COMPLETED/CANCELLED rows.
  const canPay = status === "CONFIRMED" && booking.id !== undefined;
  // Review is gated to COMPLETED (a review requires a completed booking). We need a real
  // booking id to POST /api/reviews; without one there's nothing to review.
  const canReview = status === "COMPLETED" && booking.id !== undefined;

  return (
    <Card as="li" padded className="py-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-foreground">
            {booking.serviceTitle ?? `Service #${booking.serviceId ?? "?"}`}
          </h3>
          {booking.providerName ? (
            <p className="mt-0.5 text-sm text-muted-foreground">
              Provider: {booking.providerName}
            </p>
          ) : null}
        </div>
        <BookingStatusBadge status={status} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <MetaCell label="Start">{formatDateTime(booking.startTime)}</MetaCell>
        <MetaCell label="End">{formatDateTime(booking.endTime)}</MetaCell>
        <MetaCell label="Quantity">{booking.quantity ?? 1}</MetaCell>
        <MetaCell label="Total">
          {formatPrice(booking.totalPrice, booking.currency)}
        </MetaCell>
      </dl>

      {booking.notes ? (
        <p className="mt-4 rounded-2xl bg-muted/70 p-3 text-sm text-muted-foreground">
          <span className="font-medium text-foreground">Note:</span>{" "}
          {booking.notes}
        </p>
      ) : null}

      {/*
        Actions row. We lay Cancel + Pay now side by side when both apply — but in
        practice they're mutually exclusive by status (PENDING vs CONFIRMED), so only
        one renders at a time. The `gap-3` keeps spacing sane if that ever changes.
      */}
      {canCancel || canPay ? (
        <div className="mt-4 flex flex-wrap gap-3">
          {canCancel ? (
            <Button
              variant="destructiveOutline"
              size="sm"
              isLoading={isCancelling}
              onClick={() => onCancel?.(booking.id ?? 0)}
            >
              {isCancelling ? "Cancelling…" : "Cancel booking"}
            </Button>
          ) : null}

          {/*
            Pay now → /checkout/<bookingId>. A <Link> (not a button) because it's pure
            navigation; the checkout route owns all the order/payment logic. Styled as
            a solid button via buttonClasses so it reads as the primary CTA for a
            CONFIRMED booking.
          */}
          {canPay ? (
            <Link
              href={`/checkout/${encodeURIComponent(booking.id as number)}`}
              className={buttonClasses({ variant: "primary", size: "sm" })}
            >
              Pay now
            </Link>
          ) : null}
        </div>
      ) : null}

      {/*
        Review affordance for COMPLETED bookings. The form manages its own open/submit/
        settled state (including the optimistic "already reviewed" → 422 handling), so we
        just hand it the booking id. Rendered below the cancel/pay row, but since review
        is COMPLETED-gated and those are PENDING/CONFIRMED-gated, only one ever shows.
      */}
      {canReview ? <ReviewForm bookingId={booking.id as number} /> : null}

      {cancelError ? (
        <p className="mt-3 text-sm text-danger" role="alert">
          {cancelError}
        </p>
      ) : null}
    </Card>
  );
}
