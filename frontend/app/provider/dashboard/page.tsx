"use client";

import { RequireAuth } from "@/components/require-auth";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { Container, PageHeader } from "@/components/ui/container";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge, BookingStatusBadge } from "@/components/ui/badge";
import { StarRating } from "@/components/ui/star-rating";
import {
  useProviderEarnings,
  useProviderStats,
} from "@/lib/api/provider-dashboard-queries";
import type { BookingStatus } from "@/lib/api/bookings";
import type { ProviderEarnings, ProviderStats } from "@/lib/api/provider-dashboard";

const BOOKING_STATUSES: BookingStatus[] = [
  "PENDING",
  "CONFIRMED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
];

const USD_FORMATTER = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

export default function ProviderDashboardPage() {
  return (
    <RequireAuth requireRole="VENDOR">
      <ProviderDashboardContent />
    </RequireAuth>
  );
}

function ProviderDashboardContent() {
  const statsQuery = useProviderStats();
  const earningsQuery = useProviderEarnings();

  if (statsQuery.isPending || earningsQuery.isPending) {
    return (
      <Container width="wide">
        <PageHeader
          title="Dashboard"
          subtitle="Track your home services, bookings, ratings, and earnings."
        />
        <CatalogSkeleton />
      </Container>
    );
  }

  if (statsQuery.isError || earningsQuery.isError) {
    return (
      <Container width="wide">
        <PageHeader
          title="Dashboard"
          subtitle="Track your home services, bookings, ratings, and earnings."
        />
        <ErrorState
          error={statsQuery.error ?? earningsQuery.error}
          onRetry={() => {
            void statsQuery.refetch();
            void earningsQuery.refetch();
          }}
          title="Couldn't load dashboard."
        />
      </Container>
    );
  }

  return (
    <Container width="wide">
      <PageHeader
        title="Dashboard"
        subtitle="Track your home services, bookings, ratings, and earnings."
      />

      <section className="mb-8">
        <h2 className="mb-4 text-xl font-semibold tracking-tight">Earnings</h2>
        <EarningsGrid earnings={earningsQuery.data ?? {}} />
      </section>

      <section>
        <h2 className="mb-4 text-xl font-semibold tracking-tight">Provider stats</h2>
        <StatsGrid stats={statsQuery.data ?? {}} />
      </section>
    </Container>
  );
}

function EarningsGrid({ earnings }: { earnings: ProviderEarnings }) {
  const monthlyEntries = Object.entries(earnings.earningsByMonth ?? {}).sort(
    ([a], [b]) => b.localeCompare(a),
  );

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <MetricCard
        label="Total earnings"
        value={formatMoney(earnings.totalEarnings)}
        hint="Paid + completed booking subtotals."
      />
      <MetricCard
        label="Pending payouts"
        value={formatMoney(earnings.pendingPayouts)}
        hint="Paid bookings not completed yet."
      />
      <MetricCard
        label="Paid out"
        value={formatMoney(earnings.paidOut)}
        hint="Completed booking subtotals."
      />

      <Card className="lg:col-span-3">
        <CardHeader>
          <CardTitle>Monthly earnings</CardTitle>
        </CardHeader>
        <CardBody>
          {monthlyEntries.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No paid earnings yet. Once customers pay for bookings, monthly totals
              will appear here.
            </p>
          ) : (
            <ul className="divide-y divide-border/70">
              {monthlyEntries.map(([month, amount]) => (
                <li key={month} className="flex items-center justify-between py-3">
                  <span className="font-medium text-foreground">{month}</span>
                  <span className="text-muted-foreground">{formatMoney(amount)}</span>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

function StatsGrid({ stats }: { stats: ProviderStats }) {
  const bookingsByStatus = stats.bookingsByStatus ?? {};

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <MetricCard
        label="Services"
        value={`${stats.activeServices ?? 0} active`}
        hint={`${stats.totalServices ?? 0} total services`}
      />
      <MetricCard
        label="Total bookings"
        value={stats.totalBookings ?? 0}
        hint={`${stats.pendingBookings ?? 0} pending, ${stats.confirmedBookings ?? 0} confirmed`}
      />
      <Card padded>
        <p className="text-sm font-medium text-muted-foreground">Average rating</p>
        <div className="mt-3 flex items-center gap-3">
          <StarRating value={stats.averageRating ?? 0} showValue />
          <Badge tone="neutral">{stats.totalReviews ?? 0} reviews</Badge>
        </div>
        <p className="mt-3 text-sm text-muted-foreground">
          {stats.totalCustomers ?? 0} unique customer
          {(stats.totalCustomers ?? 0) === 1 ? "" : "s"}
        </p>
      </Card>

      <Card className="lg:col-span-3">
        <CardHeader>
          <CardTitle>Booking breakdown</CardTitle>
        </CardHeader>
        <CardBody>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {BOOKING_STATUSES.map((status) => (
              <div
                key={status}
                className="rounded-2xl border border-border/60 bg-background/60 p-4"
              >
                <BookingStatusBadge status={status} />
                <p className="mt-3 text-2xl font-semibold">
                  {bookingsByStatus[status] ?? 0}
                </p>
              </div>
            ))}
          </div>
        </CardBody>
      </Card>
    </div>
  );
}

function MetricCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: string | number;
  hint: string;
}) {
  return (
    <Card padded>
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      <p className="mt-2 text-3xl font-bold tracking-tight text-foreground">
        {value}
      </p>
      <p className="mt-2 text-sm text-muted-foreground">{hint}</p>
    </Card>
  );
}

function formatMoney(value: number | undefined): string {
  return USD_FORMATTER.format(value ?? 0);
}
