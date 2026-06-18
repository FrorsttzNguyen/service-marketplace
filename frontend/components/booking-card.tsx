"use client";

/**
 * Booking row — one entry in the "My bookings" list.
 *
 * Shows the service title, vendor, time window (formatted), status badge, price, and
 * quantity. Two status-gated actions:
 *   - Cancel: PENDING only (the backend rejects cancel for any other status with 422).
 *     Clicking it calls the cancel mutation the parent wires up. A 422 (status changed
 *     under us) surfaces the server message and the parent refetches.
 *   - Pay now: CONFIRMED only. A booking must be CONFIRMED before an order can be
 *     created from it (the backend rejects POST /api/orders with 422 otherwise). The
 *     button is a plain link to /checkout/<bookingId> — the checkout page handles the
 *     order → payment → Stripe flow.
 */
import Link from "next/link";
import type { Booking, BookingStatus } from "@/lib/api/bookings";

interface BookingCardProps {
  booking: Booking;
  /** True while a cancel mutation is in flight for THIS booking (disables the button). */
  isCancelling?: boolean;
  /** Called when the user clicks Cancel. Parent decides confirm + mutation. */
  onCancel?: (id: number) => void;
  /** Error message from a failed cancel for THIS booking (e.g. the 422 message). */
  cancelError?: string | null;
}

/** Map a booking status to a Tailwind color class for the badge. */
function statusBadgeClass(status: BookingStatus): string {
  switch (status) {
    case "PENDING":
      return "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300";
    case "CONFIRMED":
      return "bg-blue-100 text-blue-800 dark:bg-blue-950/50 dark:text-blue-300";
    case "IN_PROGRESS":
      return "bg-indigo-100 text-indigo-800 dark:bg-indigo-950/50 dark:text-indigo-300";
    case "COMPLETED":
      return "bg-green-100 text-green-800 dark:bg-green-950/50 dark:text-green-300";
    case "CANCELLED":
      return "bg-neutral-200 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-400";
    default:
      return "bg-neutral-100 text-neutral-700 dark:bg-neutral-900 dark:text-neutral-400";
  }
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

export function BookingCard({
  booking,
  isCancelling = false,
  onCancel,
  cancelError = null,
}: BookingCardProps) {
  const status = booking.status ?? "PENDING";
  const canCancel = status === "PENDING" && onCancel !== undefined;
  // Pay-now is gated to CONFIRMED: only confirmed bookings can seed an order. The
  // checkout page will also enforce this server-side (422), but gating here avoids a
  // pointless navigation for PENDING/COMPLETED/CANCELLED rows.
  const canPay = status === "CONFIRMED" && booking.id !== undefined;

  return (
    <li className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            {booking.serviceTitle ?? `Service #${booking.serviceId ?? "?"}`}
          </h3>
          {booking.vendorName ? (
            <p className="text-sm text-neutral-500 dark:text-neutral-400">
              by {booking.vendorName}
            </p>
          ) : null}
        </div>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusBadgeClass(status)}`}
        >
          {status}
        </span>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">Start</dt>
          <dd>{formatDateTime(booking.startTime)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">End</dt>
          <dd>{formatDateTime(booking.endTime)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">Quantity</dt>
          <dd>{booking.quantity ?? 1}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">Total</dt>
          <dd>{formatPrice(booking.totalPrice, booking.currency)}</dd>
        </div>
      </dl>

      {booking.notes ? (
        <p className="mt-3 rounded bg-neutral-50 p-2 text-sm text-neutral-600 dark:bg-neutral-900 dark:text-neutral-400">
          <span className="font-medium">Note:</span> {booking.notes}
        </p>
      ) : null}

      {/*
        Actions row. We lay Cancel + Pay now side by side when both apply — but in
        practice they're mutually exclusive by status (PENDING vs CONFIRMED), so only
        one renders at a time. The `gap-3` keeps spacing sane if that ever changes.
      */}
      {(canCancel || canPay) ? (
        <div className="mt-3 flex flex-wrap gap-3">
          {canCancel ? (
            <button
              type="button"
              disabled={isCancelling}
              onClick={() => onCancel?.(booking.id ?? 0)}
              className="rounded border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
            >
              {isCancelling ? "Cancelling…" : "Cancel booking"}
            </button>
          ) : null}

          {/*
            Pay now → /checkout/<bookingId>. A <Link> (not a button) because it's pure
            navigation; the checkout route owns all the order/payment logic. Styled as
            a solid button so it reads as the primary CTA for a CONFIRMED booking.
          */}
          {canPay ? (
            <Link
              href={`/checkout/${encodeURIComponent(booking.id as number)}`}
              className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700"
            >
              Pay now
            </Link>
          ) : null}
        </div>
      ) : null}

      {cancelError ? (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400" role="alert">
          {cancelError}
        </p>
      ) : null}
    </li>
  );
}
