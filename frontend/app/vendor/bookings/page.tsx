"use client";

/**
 * Vendor — incoming bookings (`/vendor/bookings`).
 *
 * The backend's two vendor-side booking endpoints power this page:
 *   - GET /api/bookings/vendor?page&size  (list bookings on this vendor's services,
 *                                         ALL statuses)
 *   - PUT /api/bookings/{id}/confirm      (PENDING → CONFIRMED)
 *
 * This is the missing half of the "book → confirm → pay" flow: a customer books
 * (creates a PENDING booking) on the public catalog, the vendor confirms it here, and
 * ONLY then can the customer pay (an Order requires a CONFIRMED booking). With this
 * page the whole flow is now clickable end-to-end in the browser.
 *
 * SCOPE NOTE: the backend exposes ONLY `confirm` to the vendor UI. start/complete are
 * NOT wired here (they transition via other flows) — do not call any start/complete
 * endpoint.
 *
 * Gated two ways (mirrors /vendor/services + /admin/vendors):
 *   - Client route guard: <RequireAuth requireRole="VENDOR"> redirects non-vendors home.
 *     Depends on the /me-in-rehydrate wiring so `user.role` survives a reload.
 *   - Server-side: both endpoints require a VENDOR-role JWT (and confirm requires the
 *     booking's service to belong to this vendor). 403 is the real security boundary.
 *
 * UI shape mirrors "My bookings": loading skeleton → error card (with retry) → empty
 * state → list of rows + pagination. Each PENDING row gets a Confirm button (per-row
 * spinner + error). The row component is vendor-specific (it shows the CUSTOMER name
 * and offers Confirm), so it lives in this file rather than reusing the customer
 * <BookingCard> — same data shape, different actions + emphasis.
 */
import { useState } from "react";
import { RequireAuth } from "@/components/require-auth";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { ApiError } from "@/lib/api/client";
import {
  useConfirmBooking,
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

  // Track WHICH booking is currently confirming (only one button spins at a time) and
  // the per-row error so a 403/404/422 message shows next to the right row, not globally.
  // Mirrors the customer-side cancel state in app/bookings/page.tsx.
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [confirmErrorId, setConfirmErrorId] = useState<number | null>(null);
  const [confirmErrorMsg, setConfirmErrorMsg] = useState<string | null>(null);

  function handleConfirm(id: number) {
    // Confirming lets the customer pay — it's a commitment, so a quick confirm is
    // appropriate (matches the app's window.confirm pattern elsewhere).
    if (!window.confirm("Confirm this booking? The customer can then pay.")) {
      return;
    }
    setConfirmingId(id);
    setConfirmErrorId(null);
    setConfirmErrorMsg(null);
    confirmMutation.mutate(id, {
      onSuccess: () => {
        setConfirmingId(null);
        // invalidation in the hook refetches the list automatically; the row's Confirm
        // button disappears (it's PENDING-gated) and the status flips to CONFIRMED.
      },
      onError: (err: unknown) => {
        setConfirmingId(null);
        setConfirmErrorId(id);
        // 422 = status changed (e.g. customer cancelled between our list render and the
        // click, or it was already confirmed); show the server's message and let the
        // refetch fix the UI.
        setConfirmErrorMsg(
          err instanceof ApiError
            ? err.message
            : "Couldn't confirm this booking.",
        );
      },
    });
  }

  const bookings = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">Bookings</h1>
        <p className="mt-1 text-neutral-600 dark:text-neutral-400">
          Requests customers have made on your services. Confirm pending ones to let
          them pay.
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
                    isConfirming={confirmingId === booking.id}
                    confirmError={
                      confirmErrorId === booking.id ? confirmErrorMsg : null
                    }
                    onConfirm={handleConfirm}
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
  isConfirming: boolean;
  confirmError: string | null;
  onConfirm: (id: number) => void;
}

function VendorBookingRow({
  booking,
  isConfirming,
  confirmError,
  onConfirm,
}: VendorBookingRowProps) {
  const status = booking.status ?? "PENDING";
  // Confirm only makes sense for PENDING — the backend rejects it otherwise (422). The
  // vendor sees historical rows (CONFIRMED/COMPLETED/CANCELLED) read-only.
  const canConfirm = status === "PENDING" && booking.id !== undefined;

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

      {canConfirm ? (
        <div className="mt-3 flex flex-wrap gap-3">
          <button
            type="button"
            disabled={isConfirming}
            onClick={() => onConfirm(booking.id ?? 0)}
            className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            {isConfirming ? "Confirming…" : "Confirm"}
          </button>
        </div>
      ) : null}

      {confirmError ? (
        <p className="mt-2 text-sm text-red-600 dark:text-red-400" role="alert">
          {confirmError}
        </p>
      ) : null}
    </li>
  );
}
