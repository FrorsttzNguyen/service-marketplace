"use client";

/**
 * "My bookings" page — `GET /api/bookings` (paginated, JWT-scoped server-side).
 *
 * Wrapped in <RequireAuth> so unauthenticated visitors are redirected to /login (with
 * a redirect back here). Renders a paginated list of booking rows; the Cancel button
 * is gated to PENDING-only inside <BookingCard>, and on click we run the cancel
 * mutation. A 422 (status changed under us) surfaces the server message and the
 * invalidation-on-success refetches the list with the true status.
 */
import { useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/require-auth";
import { BookingCard } from "@/components/booking-card";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { ApiError } from "@/lib/api/client";
import { useMyBookings, useCancelBooking } from "@/lib/api/bookings-queries";

const PAGE_SIZE = 10;

export default function BookingsPage() {
  return (
    <RequireAuth>
      <BookingsContent />
    </RequireAuth>
  );
}

function BookingsContent() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error, refetch, isFetching } = useMyBookings({
    page,
    size: PAGE_SIZE,
  });

  const cancelMutation = useCancelBooking();

  // Track WHICH booking is currently cancelling (only one button spins at a time) and
  // the per-row cancel error so a 422 message shows next to the right row, not globally.
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [cancelErrorId, setCancelErrorId] = useState<number | null>(null);
  const [cancelErrorMsg, setCancelErrorMsg] = useState<string | null>(null);

  function handleCancel(id: number) {
    // Simple confirm — good enough for a portfolio app; a real app would use a modal.
    if (
      !window.confirm("Cancel this booking? This usually can't be undone.")
    ) {
      return;
    }
    setCancellingId(id);
    setCancelErrorId(null);
    setCancelErrorMsg(null);
    cancelMutation.mutate(id, {
      onSuccess: () => {
        setCancellingId(null);
        // invalidation in the hook refetches the list automatically.
      },
      onError: (err: unknown) => {
        setCancellingId(null);
        setCancelErrorId(id);
        // 422 = status changed; show the server's message and let the refetch fix the UI.
        setCancelErrorMsg(
          err instanceof ApiError
            ? err.message
            : "Couldn't cancel this booking.",
        );
      },
    });
  }

  const bookings = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">My bookings</h1>
          <p className="mt-1 text-neutral-600 dark:text-neutral-400">
            Services you&apos;ve requested.
          </p>
        </div>
      </header>

      {isPending ? (
        <CatalogSkeleton />
      ) : isError ? (
        <ErrorState
          error={error}
          onRetry={() => refetch()}
          title="Couldn't load your bookings."
        />
      ) : (
        <>
          {isFetching ? (
            <p className="mb-4 text-sm text-neutral-500">Refreshing…</p>
          ) : null}

          {bookings.length === 0 ? (
            <div className="rounded border border-dashed border-neutral-300 p-8 text-center dark:border-neutral-700">
              <p className="text-neutral-500 dark:text-neutral-400">
                You have no bookings yet.
              </p>
              <Link
                href="/"
                className="mt-3 inline-block text-blue-600 hover:underline dark:text-blue-400"
              >
                Browse services →
              </Link>
            </div>
          ) : (
            <>
              <p className="mb-4 text-sm text-neutral-600 dark:text-neutral-400">
                {total} booking{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-3">
                {bookings.map((booking) => (
                  <BookingCard
                    key={booking.id ?? Math.random()}
                    booking={booking}
                    isCancelling={cancellingId === booking.id}
                    cancelError={
                      cancelErrorId === booking.id ? cancelErrorMsg : null
                    }
                    onCancel={handleCancel}
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
