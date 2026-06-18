"use client";

/**
 * Vendor — incoming bookings (`/vendor/bookings`).
 *
 * The vendor drives the booking lifecycle forward through three bodyless PUTs, plus a
 * paginated list of every booking on their services (all statuses):
 *   - GET    /api/bookings/vendor?page&size  (list, ALL statuses)
 *   - PUT    /api/bookings/{id}/confirm      (PENDING     → CONFIRMED)
 *   - PUT    /api/bookings/{id}/start        (CONFIRMED   → IN_PROGRESS)
 *   - PUT    /api/bookings/{id}/complete     (IN_PROGRESS → COMPLETED)
 *
 * Full lifecycle: PENDING → CONFIRMED → IN_PROGRESS → COMPLETED (CANCELLED is a side
 * exit the customer can take from PENDING). Each row shows the single action button
 * appropriate to its status — Confirm / Start / Complete — and COMPLETED + CANCELLED
 * rows are read-only. Completing is the final vendor step and is also what unblocks the
 * customer to leave a review (a review requires a COMPLETED booking).
 *
 * Gated two ways (mirrors /vendor/services + /admin/vendors):
 *   - Client route guard: <RequireAuth requireRole="VENDOR"> redirects non-vendors home.
 *     Depends on the /me-in-rehydrate wiring so `user.role` survives a reload.
 *   - Server-side: every endpoint requires a VENDOR-role JWT (and the transition
 *     endpoints require the booking's service to belong to this vendor). 403 is the real
 *     security boundary.
 *
 * UI shape mirrors "My bookings": loading skeleton → error card (with retry) → empty
 * state → list of rows + pagination. Each actionable row gets one button (per-row
 * spinner + per-row error). The per-row state is generalized to cover all three
 * transitions: a single `actioningId` (only one row spins at a time) +
 * `actionErrorId`/`actionErrorMsg` so a 403/404/422 message lands on the right row.
 * The row component is vendor-specific (it shows the CUSTOMER name and offers lifecycle
 * actions), so it lives in this file rather than reusing the customer <BookingCard> —
 * same data shape, different actions + emphasis.
 */
import { useState } from "react";
import type { UseMutationResult } from "@tanstack/react-query";
import { RequireAuth } from "@/components/require-auth";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { ApiError } from "@/lib/api/client";
import {
  useCompleteBooking,
  useConfirmBooking,
  useStartBooking,
  useVendorBookings,
} from "@/lib/api/vendor-bookings-queries";
import type { Booking, BookingStatus } from "@/lib/api/bookings";

const PAGE_SIZE = 10;

export default function VendorBookingsPage() {
  return (
    <RequireAuth requireRole="VENDOR">
      <VendorBookingsContent />
    </RequireAuth>
  );
}

function VendorBookingsContent() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error, refetch, isFetching } =
    useVendorBookings({ page, size: PAGE_SIZE });

  const confirmMutation = useConfirmBooking();
  const startMutation = useStartBooking();
  const completeMutation = useCompleteBooking();

  // Per-row action state, generalized across all three transitions: only one row's
  // button spins at a time (`actioningId`), and a 403/404/422 message lands on the row
  // that produced it (`actionErrorId`/`actionErrorMsg`). Mirrors the customer-side
  // cancel state in app/bookings/page.tsx, just shared across confirm/start/complete.
  const [actioningId, setActioningId] = useState<number | null>(null);
  const [actionErrorId, setActionErrorId] = useState<number | null>(null);
  const [actionErrorMsg, setActionErrorMsg] = useState<string | null>(null);

  /**
   * Run one of the three lifecycle transitions on a booking. All three mutations share
   * the same `(id: number) => Booking` shape and the same success/error handling, so a
   * single dispatcher covers them without three near-identical handlers. On a 422
   * ("status changed under us" / "not your service" / wrong source status) we surface
   * the server message; the hook's invalidation-on-success refetches the list with the
   * true status.
   *
   * `verb` is only used to build the fallback error message ("Couldn't {verb} this
   * booking.") when the server didn't send one. Each transition's in-flight button
   * label is computed in the row from the status, not passed through here.
   */
  function runTransition(
    id: number,
    mutation: UseMutationResult<Booking, unknown, number>,
    verb: string,
    prompt: string,
  ) {
    if (!window.confirm(prompt)) return;
    setActioningId(id);
    setActionErrorId(null);
    setActionErrorMsg(null);
    mutation.mutate(id, {
      onSuccess: () => {
        setActioningId(null);
        // invalidation in the hook refetches the list automatically; the row's button
        // flips to the next transition (or disappears for COMPLETED).
      },
      onError: (err: unknown) => {
        setActioningId(null);
        setActionErrorId(id);
        // 422 = the status no longer allows this transition (race with the customer, or
        // the row was already advanced); show the server's message and let the refetch
        // fix the UI.
        setActionErrorMsg(
          err instanceof ApiError ? err.message : `Couldn't ${verb} this booking.`,
        );
      },
    });
  }

  function handleConfirm(id: number) {
    runTransition(
      id,
      confirmMutation,
      "confirm",
      "Confirm this booking? The customer can then pay.",
    );
  }

  function handleStart(id: number) {
    runTransition(
      id,
      startMutation,
      "start",
      "Start this booking? It will be marked as in progress.",
    );
  }

  function handleComplete(id: number) {
    runTransition(
      id,
      completeMutation,
      "complete",
      "Complete this booking? The customer will be able to leave a review.",
    );
  }

  const bookings = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">Bookings</h1>
        <p className="mt-1 text-neutral-600 dark:text-neutral-400">
          Requests customers have made on your services. Advance them through confirm →
          start → complete.
        </p>
      </header>

      {isPending ? (
        <CatalogSkeleton />
      ) : isError ? (
        <ErrorState
          error={error}
          onRetry={() => refetch()}
          title="Couldn't load bookings."
        />
      ) : (
        <>
          {isFetching ? (
            <p className="mb-4 text-sm text-neutral-500">Refreshing…</p>
          ) : null}

          {bookings.length === 0 ? (
            <div className="rounded border border-dashed border-neutral-300 p-8 text-center dark:border-neutral-700">
              <p className="text-neutral-500 dark:text-neutral-400">
                No bookings yet. When a customer books one of your services, it&apos;ll
                appear here.
              </p>
            </div>
          ) : (
            <>
              <p className="mb-4 text-sm text-neutral-600 dark:text-neutral-400">
                {total} booking{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-3">
                {bookings.map((booking) => (
                  <VendorBookingRow
                    key={booking.id ?? Math.random()}
                    booking={booking}
                    actionInFlight={actioningId === booking.id}
                    actionError={
                      actionErrorId === booking.id ? actionErrorMsg : null
                    }
                    onConfirm={handleConfirm}
                    onStart={handleStart}
                    onComplete={handleComplete}
                  />
                ))}
              </ul>
              <Pagination
                number={data?.number}
                totalPages={data?.totalPages}
                first={data?.first}
                last={data?.last}
                onPageChange={setPage}
                disabled={isFetching}
              />
            </>
          )}
        </>
      )}
    </main>
  );
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
  return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/** Format a price + currency code (e.g. 12.5 + "USD" → "$12.50"). */
function formatPrice(
  amount: number | undefined,
  currency: string | undefined,
): string {
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

interface VendorBookingRowProps {
  booking: Booking;
  actionInFlight: boolean;
  actionError: string | null;
  onConfirm: (id: number) => void;
  onStart: (id: number) => void;
  onComplete: (id: number) => void;
}

/**
 * Decide the single action (if any) a vendor can take on a booking given its status.
 * The backend rejects a transition from the wrong source status with 422, so gating the
 * button to the correct status here avoids a pointless round-trip AND keeps each row's
 * affordance unambiguous: exactly one next step, or none for terminal states.
 *
 * Returns `{ label, onClick, inFlightLabel }` or `null` for read-only rows.
 */
function actionForStatus(
  status: BookingStatus,
  bookingId: number | undefined,
  onConfirm: (id: number) => void,
  onStart: (id: number) => void,
  onComplete: (id: number) => void,
): { label: string; inFlightLabel: string; onClick: () => void } | null {
  if (bookingId === undefined) return null;
  switch (status) {
    case "PENDING":
      return {
        label: "Confirm",
        inFlightLabel: "Confirming…",
        onClick: () => onConfirm(bookingId),
      };
    case "CONFIRMED":
      return {
        label: "Start",
        inFlightLabel: "Starting…",
        onClick: () => onStart(bookingId),
      };
    case "IN_PROGRESS":
      return {
        label: "Complete",
        inFlightLabel: "Completing…",
        onClick: () => onComplete(bookingId),
      };
    // COMPLETED + CANCELLED are terminal — no vendor action. Read-only.
    default:
      return null;
  }
}

function VendorBookingRow({
  booking,
  actionInFlight,
  actionError,
  onConfirm,
  onStart,
  onComplete,
}: VendorBookingRowProps) {
  const status = booking.status ?? "PENDING";
  const action = actionForStatus(
    status,
    booking.id,
    onConfirm,
    onStart,
    onComplete,
  );

  return (
    <li className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            {booking.serviceTitle ?? `Service #${booking.serviceId ?? "?"}`}
          </h3>
          {/*
            For the vendor, the CUSTOMER is the "other party" (vs. the customer view,
            which shows the vendor). Lead with the customer name so the vendor knows who
            the request is from.
          */}
          {booking.customerName ? (
            <p className="text-sm text-neutral-500 dark:text-neutral-400">
              from {booking.customerName}
            </p>
          ) : null}
        </div>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusBadgeClass(
            status,
          )}`}
        >
          {status}
        </span>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            Start
          </dt>
          <dd>{formatDateTime(booking.startTime)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">End</dt>
          <dd>{formatDateTime(booking.endTime)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            Quantity
          </dt>
          <dd>{booking.quantity ?? 1}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            Total
          </dt>
          <dd>{formatPrice(booking.totalPrice, booking.currency)}</dd>
        </div>
      </dl>

      {booking.notes ? (
        <p className="mt-3 rounded bg-neutral-50 p-2 text-sm text-neutral-600 dark:bg-neutral-900 dark:text-neutral-400">
          <span className="font-medium">Note:</span> {booking.notes}
        </p>
      ) : null}

      {/*
        Single status-appropriate action button. The label + in-flight label come from
        `actionForStatus` so Confirm/Start/Complete each read naturally. Disabled (and
        showing the gerund) while any action is mid-flight for THIS row.
      */}
      {action ? (
        <div className="mt-3 flex flex-wrap gap-3">
          <button
            type="button"
            disabled={actionInFlight}
            onClick={action.onClick}
            className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            {actionInFlight ? action.inFlightLabel : action.label}
          </button>
        </div>
      ) : null}

      {actionError ? (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400" role="alert">
          {actionError}
        </p>
      ) : null}
    </li>
  );
}
