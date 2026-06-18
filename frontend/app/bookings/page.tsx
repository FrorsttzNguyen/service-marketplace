"use client";

/**
 * "My bookings" page — `GET /api/bookings` (paginated, JWT-scoped server-side).
 *
 * Wrapped in <RequireAuth> so unauthenticated visitors are redirected to /login (with
 * a redirect back here). Renders a paginated list of booking rows; the Cancel button
 * is gated to PENDING-only inside <BookingCard>, and on click we run the cancel
 * mutation. A 422 (status changed under us) surfaces the server message and the
 * invalidation-on-success refetches the list with the true status.
 *
 * Visual (Phase 7): PageHeader + island list of booking cards. Empty state is a soft
 * dashed island with a CTA back to the catalog.
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
import { Container, PageHeader } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { buttonClasses } from "@/components/ui/button";

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
    if (!window.confirm("Cancel this booking? This usually can't be undone.")) {
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
          err instanceof ApiError ? err.message : "Couldn't cancel this booking.",
        );
      },
    });
  }

  const bookings = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <Container width="default">
      <PageHeader
        title="My bookings"
        subtitle="Services you've requested."
      />

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
            <p className="mb-4 text-sm text-muted-foreground">Refreshing…</p>
          ) : null}

          {bookings.length === 0 ? (
            <Card padded className="py-10 text-center">
              <p className="text-muted-foreground">You have no bookings yet.</p>
              <Link
                href="/"
                className={buttonClasses({
                  variant: "primary",
                  size: "sm",
                  className: "mt-4",
                })}
              >
                Browse services →
              </Link>
            </Card>
          ) : (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                {total} booking{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-4">
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
    </Container>
  );
}
