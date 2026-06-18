"use client";

/**
 * Admin — vendor approval queue (`/admin/vendors`).
 *
 * The backend's three admin endpoints power this single page:
 *   - GET    /api/admin/vendors?status=...&page=...&size=...   (list, filtered by status)
 *   - POST   /api/admin/vendors/{vendorId}/approve             (approve → APPROVED)
 *   - POST   /api/admin/vendors/{vendorId}/reject              (reject  → REJECTED)
 *
 * Gated two ways:
 *   - Client route guard: <RequireAuth requireRole="ADMIN"> redirects non-admins home
 *     (and the unauthenticated to /login). This is the first line of defense in normal
 *     navigation; it depends on the /me-in-rehydrate wiring so `user.role` survives a
 *     reload (a reloaded admin would otherwise look role-less and get bounced).
 *   - Server-side: every endpoint requires an ADMIN-role JWT and returns 403 otherwise.
 *     The route guard is UX; the 403 is the actual security boundary. We surface a 403
 *     as an explicit error state rather than silently redirecting, so a misbehaving
 *     client can't get stuck in a loop.
 *
 * UI shape mirrors "My bookings": loading skeleton → error card (with retry) → empty
 * state → list of rows + pagination. Each PENDING row exposes Approve + Reject; rows in
 * other statuses are read-only (you can still see them via the filter). Mutations
 * invalidate the whole admin-vendor query family, so the list refetches and the vendor
 * leaves the PENDING view on success.
 */
import { useState } from "react";
import { RequireAuth } from "@/components/require-auth";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { ApiError } from "@/lib/api/client";
import {
  useApproveVendor,
  useRejectVendor,
  useVendors,
} from "@/lib/api/admin-queries";
import type {
  VendorAdmin,
  VendorVerificationStatus,
} from "@/lib/api/admin";

const PAGE_SIZE = 10;

/**
 * The status tabs. `undefined` means "all statuses" — passed to the backend by OMITTING
 * the query param (the spec marks `status` optional), so the server lists every vendor
 * regardless of state. The order is PENDING first (the actionable queue) so the default
 * lands the reviewer on what needs attention.
 */
const STATUS_FILTERS: Array<{
  label: string;
  value: VendorVerificationStatus | undefined;
}> = [
  { label: "Pending", value: "PENDING" },
  { label: "Approved", value: "APPROVED" },
  { label: "Rejected", value: "REJECTED" },
  { label: "All", value: undefined },
];

export default function AdminVendorsPage() {
  return (
    <RequireAuth requireRole="ADMIN">
      <AdminVendorsContent />
    </RequireAuth>
  );
}

function AdminVendorsContent() {
  // Default to PENDING — reviewers land on the actionable queue. The filter is separate
  // from `page` because changing the filter must reset to page 0 (the new filter's result
  // set has a different length, so the old page index is meaningless).
  const [status, setStatus] = useState<VendorVerificationStatus | undefined>(
    "PENDING",
  );
  const [page, setPage] = useState(0);

  const { data, isPending, isError, error, refetch, isFetching } = useVendors({
    status,
    page,
    size: PAGE_SIZE,
  });

  const approveMutation = useApproveVendor();
  const rejectMutation = useRejectVendor();

  // Track which vendor has an action in flight (only one row's button spins at a time)
  // and the per-row error so a 404/403 message shows next to the right row, not globally.
  const [actionVendorId, setActionVendorId] = useState<number | null>(null);
  const [errorVendorId, setErrorVendorId] = useState<number | null>(null);
  const [actionErrorMsg, setActionErrorMsg] = useState<string | null>(null);

  function runAction(
    vendorId: number,
    mutation: typeof approveMutation,
    failureVerb: string,
  ) {
    setActionVendorId(vendorId);
    setErrorVendorId(null);
    setActionErrorMsg(null);
    mutation.mutate(vendorId, {
      onSuccess: () => {
        setActionVendorId(null);
        // The hook invalidates the admin-vendor family → the list refetches with the
        // vendor's new status. If we're on PENDING, the row disappears.
      },
      onError: (err: unknown) => {
        setActionVendorId(null);
        setErrorVendorId(vendorId);
        setActionErrorMsg(
          err instanceof ApiError
            ? err.message
            : `Couldn't ${failureVerb} this vendor.`,
        );
      },
    });
  }

  function handleApprove(vendorId: number) {
    // Confirm for the destructive/reversible pair — approve is low-risk but reject
    // blocks the vendor from selling. A simple window.confirm matches the bookings page.
    if (!window.confirm("Approve this vendor?")) return;
    runAction(vendorId, approveMutation, "approve");
  }

  function handleReject(vendorId: number) {
    if (!window.confirm("Reject this vendor? They won't be able to sell.")) {
      return;
    }
    runAction(vendorId, rejectMutation, "reject");
  }

  /** Switch the status filter and jump back to page 0 (see comment on `status`). */
  function changeStatus(next: VendorVerificationStatus | undefined) {
    setStatus(next);
    setPage(0);
  }

  const vendors = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">Vendor approvals</h1>
        <p className="mt-1 text-neutral-600 dark:text-neutral-400">
          Review vendors applying to sell on the marketplace.
        </p>
      </header>

      {/*
        Status filter tabs. Rendered as a row of buttons (not a <select>) so the active
        state is visible at a glance and the tabs are keyboard-reachable as buttons. The
        aria-pressed pattern communicates the active tab to screen readers.
      */}
      <div
        className="mb-6 flex flex-wrap gap-2"
        role="group"
        aria-label="Filter vendors by status"
      >
        {STATUS_FILTERS.map((filter) => {
          const active = status === filter.value;
          return (
            <button
              key={filter.label}
              type="button"
              aria-pressed={active}
              onClick={() => changeStatus(filter.value)}
              className={
                active
                  ? "rounded-full bg-blue-600 px-3 py-1 text-sm font-medium text-white"
                  : "rounded-full border border-neutral-300 px-3 py-1 text-sm text-neutral-700 hover:border-blue-400 hover:text-blue-600 dark:border-neutral-700 dark:text-neutral-300 dark:hover:text-blue-400"
              }
            >
              {filter.label}
            </button>
          );
        })}
      </div>

      {isPending ? (
        <CatalogSkeleton />
      ) : isError ? (
        <ErrorState
          error={error}
          onRetry={() => refetch()}
          title="Couldn't load vendors."
        />
      ) : (
        <>
          {isFetching ? (
            <p className="mb-4 text-sm text-neutral-500">Refreshing…</p>
          ) : null}

          {vendors.length === 0 ? (
            <div className="rounded border border-dashed border-neutral-300 p-8 text-center dark:border-neutral-700">
              <p className="text-neutral-500 dark:text-neutral-400">
                No vendors{status ? ` with status ${status}` : ""}.
              </p>
            </div>
          ) : (
            <>
              <p className="mb-4 text-sm text-neutral-600 dark:text-neutral-400">
                {total} vendor{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-3">
                {vendors.map((vendor) => (
                  <VendorRow
                    key={vendor.vendorId ?? Math.random()}
                    vendor={vendor}
                    // Action buttons only make sense for the actionable queue. Approved/
                    // rejected rows are historical — show them (via the filter) but
                    // read-only. The backend would also reject an approve on a non-PENDING
                    // vendor, so gating here avoids a pointless round-trip.
                    actionable={vendor.verificationStatus === "PENDING"}
                    actionInFlight={actionVendorId === vendor.vendorId}
                    actionError={
                      errorVendorId === vendor.vendorId ? actionErrorMsg : null
                    }
                    onApprove={handleApprove}
                    onReject={handleReject}
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

interface VendorRowProps {
  vendor: VendorAdmin;
  actionable: boolean;
  actionInFlight: boolean;
  actionError: string | null;
  onApprove: (vendorId: number) => void;
  onReject: (vendorId: number) => void;
}

/** Map a verification status to a Tailwind color class for the badge. */
function statusBadgeClass(status: VendorVerificationStatus): string {
  switch (status) {
    case "PENDING":
      return "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300";
    case "APPROVED":
      return "bg-green-100 text-green-800 dark:bg-green-950/50 dark:text-green-300";
    case "REJECTED":
      return "bg-red-100 text-red-800 dark:bg-red-950/50 dark:text-red-300";
    default:
      return "bg-neutral-100 text-neutral-700 dark:bg-neutral-900 dark:text-neutral-400";
  }
}

/** Format a date-time ISO string as a readable local date, or "—" if missing/invalid. */
function formatDate(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function VendorRow({
  vendor,
  actionable,
  actionInFlight,
  actionError,
  onApprove,
  onReject,
}: VendorRowProps) {
  const status = vendor.verificationStatus ?? "PENDING";
  // Both buttons are disabled while any action is in flight for THIS vendor. We don't
  // globally disable all rows because an admin reviewing a queue may want to action
  // several vendors in parallel — but two actions on the SAME vendor would race, so we
  // gate per-row.
  const disabled = actionInFlight;
  const vendorId = vendor.vendorId ?? 0;

  return (
    <li className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            {vendor.businessName || `Vendor #${vendor.vendorId ?? "?"}`}
          </h3>
          {vendor.email ? (
            <p className="text-sm text-neutral-500 dark:text-neutral-400">
              {vendor.email}
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

      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            Applied
          </dt>
          <dd>{formatDate(vendor.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            Vendor ID
          </dt>
          <dd>{vendor.vendorId ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-neutral-400">
            User ID
          </dt>
          <dd>{vendor.userId ?? "—"}</dd>
        </div>
      </dl>

      {actionable ? (
        <div className="mt-3 flex flex-wrap gap-3">
          <button
            type="button"
            disabled={disabled}
            onClick={() => onApprove(vendorId)}
            className="rounded bg-green-600 px-3 py-1 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50 dark:bg-green-700 dark:hover:bg-green-600"
          >
            {actionInFlight ? "Working…" : "Approve"}
          </button>
          <button
            type="button"
            disabled={disabled}
            onClick={() => onReject(vendorId)}
            className="rounded border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
          >
            Reject
          </button>
        </div>
      ) : null}

      {actionError ? (
        <p
          className="mt-2 text-sm text-red-600 dark:text-red-400"
          role="alert"
        >
          {actionError}
        </p>
      ) : null}
    </li>
  );
}
