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
 *
 * Visual (Phase 7): PageHeader; status filter tabs are polished toggle pills (same
 * vocabulary as the catalog category chips); rows are island cards; status uses
 * VendorStatusBadge; actions use Button (success / destructiveOutline).
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
import { Container, PageHeader } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { VendorStatusBadge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

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
    <Container width="default">
      <PageHeader
        title="Vendor approvals"
        subtitle="Review vendors applying to sell on the marketplace."
      />

      {/*
        Status filter tabs. Rendered as a row of pill buttons (not a <select>) so the
        active state is visible at a glance and the tabs are keyboard-reachable as
        buttons. The aria-pressed pattern communicates the active tab to screen readers.
        Same pill vocabulary as the catalog category chips.
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
              className={cn(
                "rounded-pill px-4 py-1.5 text-sm font-medium transition-all",
                active
                  ? "bg-primary text-primary-foreground shadow-island"
                  : "border border-border/60 bg-card text-muted-foreground hover:border-primary/40 hover:bg-accent-soft hover:text-primary",
              )}
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
            <p className="mb-4 text-sm text-muted-foreground">Refreshing…</p>
          ) : null}

          {vendors.length === 0 ? (
            <Card padded className="py-10 text-center text-muted-foreground">
              No vendors{status ? ` with status ${status}` : ""}.
            </Card>
          ) : (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                {total} vendor{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-4">
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
    </Container>
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

/** Format a date-time ISO string as a readable local date, or "—" if missing/invalid. */
function formatDate(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
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

function VendorRow({
  vendor,
  actionable,
  actionInFlight,
  actionError,
  onApprove,
  onReject,
}: VendorRowProps) {
  const status: VendorVerificationStatus = vendor.verificationStatus ?? "PENDING";
  // Both buttons are disabled while any action is in flight for THIS vendor. We don't
  // globally disable all rows because an admin reviewing a queue may want to action
  // several vendors in parallel — but two actions on the SAME vendor would race, so we
  // gate per-row.
  const disabled = actionInFlight;
  const vendorId = vendor.vendorId ?? 0;

  return (
    <Card as="li" padded className="py-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-foreground">
            {vendor.businessName || `Vendor #${vendor.vendorId ?? "?"}`}
          </h3>
          {vendor.email ? (
            <p className="mt-0.5 text-sm text-muted-foreground">{vendor.email}</p>
          ) : null}
        </div>
        <VendorStatusBadge status={status} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <MetaCell label="Applied">{formatDate(vendor.createdAt)}</MetaCell>
        <MetaCell label="Vendor ID">{vendor.vendorId ?? "—"}</MetaCell>
        <MetaCell label="User ID">{vendor.userId ?? "—"}</MetaCell>
      </dl>

      {actionable ? (
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            variant="success"
            size="sm"
            disabled={disabled}
            onClick={() => onApprove(vendorId)}
          >
            {actionInFlight ? "Working…" : "Approve"}
          </Button>
          <Button
            variant="destructiveOutline"
            size="sm"
            disabled={disabled}
            onClick={() => onReject(vendorId)}
          >
            Reject
          </Button>
        </div>
      ) : null}

      {actionError ? (
        <p className="mt-3 text-sm text-danger" role="alert">
          {actionError}
        </p>
      ) : null}
    </Card>
  );
}
