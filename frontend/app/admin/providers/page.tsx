"use client";

/**
 * Admin — provider approval queue (`/admin/providers`).
 *
 * The backend's three admin endpoints power this single page:
 *   - GET    /api/admin/providers?status=...&page=...&size=...   (list, filtered by status)
 *   - POST   /api/admin/providers/{providerId}/approve             (approve → APPROVED)
 *   - POST   /api/admin/providers/{providerId}/reject              (reject  → REJECTED)
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
 * invalidate the whole admin-provider query family, so the list refetches and the provider
 * leaves the PENDING view on success.
 *
 * Visual (Phase 7): PageHeader; status filter tabs are polished toggle pills (same
 * vocabulary as the catalog category chips); rows are island cards; status uses
 * ProviderStatusBadge; actions use Button (success / destructiveOutline).
 */
import { useState } from "react";
import { RequireAuth } from "@/components/require-auth";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { ApiError } from "@/lib/api/client";
import {
  useApproveProvider,
  useRejectProvider,
  useProviders,
} from "@/lib/api/admin-queries";
import type {
  ProviderAdmin,
  ProviderVerificationStatus,
} from "@/lib/api/admin";
import { Container, PageHeader } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ProviderStatusBadge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 10;

/**
 * The status tabs. `undefined` means "all statuses" — passed to the backend by OMITTING
 * the query param (the spec marks `status` optional), so the server lists every provider
 * regardless of state. The order is PENDING first (the actionable queue) so the default
 * lands the reviewer on what needs attention.
 */
const STATUS_FILTERS: Array<{
  label: string;
  value: ProviderVerificationStatus | undefined;
}> = [
  { label: "Pending", value: "PENDING" },
  { label: "Approved", value: "APPROVED" },
  { label: "Rejected", value: "REJECTED" },
  { label: "All", value: undefined },
];

export default function AdminProvidersPage() {
  return (
    <RequireAuth requireRole="ADMIN">
      <AdminProvidersContent />
    </RequireAuth>
  );
}

function AdminProvidersContent() {
  // Default to PENDING — reviewers land on the actionable queue. The filter is separate
  // from `page` because changing the filter must reset to page 0 (the new filter's result
  // set has a different length, so the old page index is meaningless).
  const [status, setStatus] = useState<ProviderVerificationStatus | undefined>(
    "PENDING",
  );
  const [page, setPage] = useState(0);

  const { data, isPending, isError, error, refetch, isFetching } = useProviders({
    status,
    page,
    size: PAGE_SIZE,
  });

  const approveMutation = useApproveProvider();
  const rejectMutation = useRejectProvider();

  // Track which provider has an action in flight (only one row's button spins at a time)
  // and the per-row error so a 404/403 message shows next to the right row, not globally.
  const [actionProviderId, setActionProviderId] = useState<number | null>(null);
  const [errorProviderId, setErrorProviderId] = useState<number | null>(null);
  const [actionErrorMsg, setActionErrorMsg] = useState<string | null>(null);

  function runAction(
    providerId: number,
    mutation: typeof approveMutation,
    failureVerb: string,
  ) {
    setActionProviderId(providerId);
    setErrorProviderId(null);
    setActionErrorMsg(null);
    mutation.mutate(providerId, {
      onSuccess: () => {
        setActionProviderId(null);
        // The hook invalidates the admin-provider family → the list refetches with the
        // provider's new status. If we're on PENDING, the row disappears.
      },
      onError: (err: unknown) => {
        setActionProviderId(null);
        setErrorProviderId(providerId);
        setActionErrorMsg(
          err instanceof ApiError
            ? err.message
            : `Couldn't ${failureVerb} this provider.`,
        );
      },
    });
  }

  function handleApprove(providerId: number) {
    // Confirm for the destructive/reversible pair — approve is low-risk but reject
    // blocks the provider from selling. A simple window.confirm matches the bookings page.
    if (!window.confirm("Approve this provider?")) return;
    runAction(providerId, approveMutation, "approve");
  }

  function handleReject(providerId: number) {
    if (!window.confirm("Reject this provider? They won't be able to offer services.")) {
      return;
    }
    runAction(providerId, rejectMutation, "reject");
  }

  /** Switch the status filter and jump back to page 0 (see comment on `status`). */
  function changeStatus(next: ProviderVerificationStatus | undefined) {
    setStatus(next);
    setPage(0);
  }

  const providers = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <Container width="default">
      <PageHeader
        title="Provider approvals"
        subtitle="Review providers applying to offer home services."
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
        aria-label="Filter providers by status"
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
          title="Couldn't load providers."
        />
      ) : (
        <>
          {isFetching ? (
            <p className="mb-4 text-sm text-muted-foreground">Refreshing…</p>
          ) : null}

          {providers.length === 0 ? (
            <Card padded className="py-10 text-center text-muted-foreground">
              No providers{status ? ` with status ${status}` : ""}.
            </Card>
          ) : (
            <>
              <p className="mb-4 text-sm text-muted-foreground">
                {total} provider{total === 1 ? "" : "s"}
              </p>
              <ul className="space-y-4">
                {providers.map((provider) => (
                  <ProviderRow
                    key={provider.providerId ?? Math.random()}
                    provider={provider}
                    // Action buttons only make sense for the actionable queue. Approved/
                    // rejected rows are historical — show them (via the filter) but
                    // read-only. The backend would also reject an approve on a non-PENDING
                    // provider, so gating here avoids a pointless round-trip.
                    actionable={provider.verificationStatus === "PENDING"}
                    actionInFlight={actionProviderId === provider.providerId}
                    actionError={
                      errorProviderId === provider.providerId ? actionErrorMsg : null
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

interface ProviderRowProps {
  provider: ProviderAdmin;
  actionable: boolean;
  actionInFlight: boolean;
  actionError: string | null;
  onApprove: (providerId: number) => void;
  onReject: (providerId: number) => void;
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

function ProviderRow({
  provider,
  actionable,
  actionInFlight,
  actionError,
  onApprove,
  onReject,
}: ProviderRowProps) {
  const status: ProviderVerificationStatus = provider.verificationStatus ?? "PENDING";
  // Both buttons are disabled while any action is in flight for THIS provider. We don't
  // globally disable all rows because an admin reviewing a queue may want to action
  // several providers in parallel — but two actions on the SAME provider would race, so we
  // gate per-row.
  const disabled = actionInFlight;
  const providerId = provider.providerId ?? 0;

  return (
    <Card as="li" padded className="py-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-foreground">
            {provider.businessName || `Provider #${provider.providerId ?? "?"}`}
          </h3>
          {provider.email ? (
            <p className="mt-0.5 text-sm text-muted-foreground">{provider.email}</p>
          ) : null}
        </div>
        <ProviderStatusBadge status={status} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <MetaCell label="Applied">{formatDate(provider.createdAt)}</MetaCell>
        <MetaCell label="Provider ID">{provider.providerId ?? "—"}</MetaCell>
        <MetaCell label="User ID">{provider.userId ?? "—"}</MetaCell>
      </dl>

      {actionable ? (
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            variant="success"
            size="sm"
            disabled={disabled}
            onClick={() => onApprove(providerId)}
          >
            {actionInFlight ? "Working…" : "Approve"}
          </Button>
          <Button
            variant="destructiveOutline"
            size="sm"
            disabled={disabled}
            onClick={() => onReject(providerId)}
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
