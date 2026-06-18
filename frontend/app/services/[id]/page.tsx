"use client";

/**
 * Service detail page — `GET /api/services/{id}`.
 *
 * Reads the `[id]` route param client-side via `useParams()` (the page is a client
 * island because it uses React Query). Fetches one service and renders its full
 * details. The two failure modes are handled distinctly:
 *   - 404 → "not found" state (the service was deleted or never existed). No CORS
 *     hint, because a 404 proves the request reached the backend.
 *   - any other error → generic error state with the local-dev CORS hint, since the
 *     most common local failure is a browser CORS block (surfaces as a TypeError
 *     before any HTTP status).
 */
import Link from "next/link";
import { useParams } from "next/navigation";
import { useService } from "@/lib/api/queries";
import { ApiError } from "@/lib/api/client";
import { ErrorState } from "@/components/error-state";
import { ServiceDetailSkeleton } from "@/components/skeletons";
import { BookingForm } from "@/components/booking-form";
import { ReviewsSection } from "@/components/reviews-section";

/** Human-readable label for the pricing-type enum. */
function prettyPricingType(type: string): string {
  switch (type) {
    case "FIXED":
      return "Fixed price";
    case "HOURLY":
      return "Hourly";
    case "VARIABLE":
      return "Variable / custom quote";
    default:
      return type;
  }
}

/** Format a numeric price as USD. */
function formatPrice(price: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(price);
}

/** Render a single key/value row, hidden when value is empty. */
function DetailRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex justify-between gap-4 py-2 border-b border-neutral-100 dark:border-neutral-900">
      <dt className="text-sm text-neutral-500 dark:text-neutral-400">{label}</dt>
      <dd className="text-sm font-medium text-right">{children}</dd>
    </div>
  );
}

export default function ServiceDetailPage() {
  const params = useParams<{ id: string }>();
  const rawId = params?.id;

  // Parse the route param. A non-numeric id can't match any service — treat it as
  // "not found" (same UX as a real 404) instead of firing a doomed request.
  const id = rawId !== undefined ? Number(rawId) : NaN;
  const isValidId = Number.isInteger(id) && id > 0;

  // `enabled`-style guard: useService always calls useQuery, but with an invalid id
  // we skip the fetch and synthesize a not-found state below.
  const { data: service, isPending, isError, error, refetch } = useService(id);

  // Invalid route param → not found (no fetch made).
  if (!isValidId) {
    return (
      <DetailShell>
        <ErrorState
          error={null}
          notFound
          title="Service not found."
          hint={null}
        />
      </DetailShell>
    );
  }

  // 404 from the backend (service deleted / never existed) — distinct from a fetch
  // failure. Identified by ApiError carrying status === 404.
  const isNotFound = error instanceof ApiError && error.status === 404;

  if (isPending) {
    return (
      <DetailShell>
        <ServiceDetailSkeleton />
      </DetailShell>
    );
  }

  if (isError && isNotFound) {
    return (
      <DetailShell>
        <ErrorState
          error={error}
          notFound
          title="Service not found."
          hint={
            <span>
              This service may have been removed.{" "}
              <Link href="/" className="underline">
                Back to catalog
              </Link>
            </span>
          }
        />
      </DetailShell>
    );
  }

  if (isError) {
    return (
      <DetailShell>
        <ErrorState
          error={error}
          onRetry={() => refetch()}
          title="Couldn't load this service."
        />
      </DetailShell>
    );
  }

  const price =
    service?.basePrice !== undefined && service?.basePrice !== null
      ? `${formatPrice(service.basePrice)}${
          service.pricingType === "HOURLY" ? " /hr" : ""
        }`
      : service?.pricingType
        ? prettyPricingType(service.pricingType)
        : null;

  return (
    <DetailShell>
      <article>
        <h1 className="text-3xl font-bold tracking-tight">
          {service?.title ?? "Untitled service"}
        </h1>
        {service?.vendorName ? (
          <p className="mt-1 text-neutral-600 dark:text-neutral-400">
            by {service.vendorName}
          </p>
        ) : null}

        {service?.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={service.imageUrl}
            alt={service.title ?? "Service image"}
            className="mt-6 h-48 w-full rounded-lg border border-neutral-200 object-cover dark:border-neutral-800"
          />
        ) : null}

        {service?.description ? (
          <p className="mt-6 leading-relaxed text-neutral-700 dark:text-neutral-300">
            {service.description}
          </p>
        ) : (
          <p className="mt-6 italic text-neutral-400">No description provided.</p>
        )}

        <dl className="mt-8">
          {price ? <DetailRow label="Price">{price}</DetailRow> : null}
          {service?.pricingType ? (
            <DetailRow label="Pricing">
              {prettyPricingType(service.pricingType)}
            </DetailRow>
          ) : null}
          {service?.durationHours ? (
            <DetailRow label="Duration">
              {service.durationHours} hour{service.durationHours === 1 ? "" : "s"}
            </DetailRow>
          ) : null}
          {service?.categoryName ? (
            <DetailRow label="Category">{service.categoryName}</DetailRow>
          ) : null}
          {service?.address ? (
            <DetailRow label="Address">{service.address}</DetailRow>
          ) : null}
          {service?.city ? <DetailRow label="City">{service.city}</DetailRow> : null}
          {service?.averageRating !== undefined &&
          service?.averageRating !== null ? (
            <DetailRow label="Rating">
              ⭐ {service.averageRating.toFixed(1)}
              {service?.totalReviews !== undefined && service?.totalReviews !== null
                ? ` (${service.totalReviews} review${
                    service.totalReviews === 1 ? "" : "s"
                  })`
                : ""}
            </DetailRow>
          ) : null}
        </dl>

        {/*
          Booking form — only rendered once the service is loaded (we have a real id).
          The component handles its own auth gate (logged-out → login CTA) and the
          create-booking mutation + redirect to /bookings.
        */}
        {service?.id !== undefined ? (
          <BookingForm serviceId={service.id} />
        ) : null}
      </article>

      {/*
        Public reviews list — rendered below the article. Uses the PUBLIC
        GET /api/reviews/service/{id}, so it shows for logged-out visitors too. Rendered
        only when we have a valid service id; the section handles its own
        loading/error/empty states.
      */}
      {service?.id !== undefined ? (
        <ReviewsSection serviceId={service.id} />
      ) : null}
    </DetailShell>
  );
}

/** Shared page chrome (max width container + back link) for every detail-page state. */
function DetailShell({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <p className="mb-6">
        <Link
          href="/"
          className="text-sm text-blue-600 hover:underline dark:text-blue-400"
        >
          ← Back to catalog
        </Link>
      </p>
      {children}
    </main>
  );
}
