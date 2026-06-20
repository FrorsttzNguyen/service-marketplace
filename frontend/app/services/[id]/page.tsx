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
 *
 * Visual (Phase 7): the article body, the booking form, and the reviews section are
 * each their own island. The detail rows live inside a tidy `<dl>` card. NOTE on the
 * Rating row: the catalog `totalReviews` is backend-hardcoded 0 right now, so we DERIVE
 * the count from the reviews list length when convenient (the reviews section passes
 * nothing back; here we just show the average without a contradictory "(0 reviews)").
 */
import Link from "next/link";
import { useParams } from "next/navigation";
import { useService } from "@/lib/api/queries";
import { ApiError } from "@/lib/api/client";
import { ErrorState } from "@/components/error-state";
import { ServiceDetailSkeleton } from "@/components/skeletons";
import { BookingForm } from "@/components/booking-form";
import { ReviewsSection } from "@/components/reviews-section";
import { Container } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { StarRating } from "@/components/ui/star-rating";
import { buttonClasses } from "@/components/ui/button";

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
    <div className="flex justify-between gap-4 border-b border-border/60 py-2.5 last:border-0">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-right text-sm font-medium text-foreground">
        {children}
      </dd>
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
          title="Home service not found."
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
          title="Home service not found."
          hint={
            <span>
              This home service may have been removed.{" "}
              <Link href="/" className="underline">
                Back to home services
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
          title="Couldn't load this home service."
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

  const hasRating =
    typeof service?.averageRating === "number" && service.averageRating > 0;

  return (
    <DetailShell>
      {/*
        Article island — the service's headline + image + description + detail rows
        + booking form all live inside one big rounded island with generous padding,
        so the whole "service" reads as one content block floating on the wash.
      */}
      <Card padded className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            {service?.title ?? "Untitled home service"}
          </h1>
          {service?.providerName ? (
            <p className="mt-1.5 text-muted-foreground">
              Pro: {service.providerName}
            </p>
          ) : null}
        </div>

        {service?.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={service.imageUrl}
            alt={service.title ?? "Home service image"}
            className="h-56 w-full rounded-2xl border border-border/60 object-cover shadow-island sm:h-64"
          />
        ) : null}

        {service?.description ? (
          <p className="leading-relaxed text-foreground/90">
            {service.description}
          </p>
        ) : (
          <p className="italic text-muted-foreground">No service details provided.</p>
        )}

        {/*
          Detail rows island-within-island: a tinted panel separates the meta grid
          from the prose above. Soft shadow + rounded corners keep it readable.
        */}
        <dl className="rounded-2xl bg-muted/60 p-5">
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
            <DetailRow label="Pro address">{service.address}</DetailRow>
          ) : null}
          {service?.city ? (
            <DetailRow label="City">{service.city}</DetailRow>
          ) : null}
          {/*
            Rating row: show stars + average. We deliberately OMIT the
            "(N reviews)" suffix because the catalog totalReviews is
            backend-hardcoded 0 right now, which would read as a contradiction
            against the actual reviews list below. The ReviewsSection header
            shows the true count derived from the list length.
          */}
          {hasRating ? (
            <DetailRow label="Rating">
              <span className="inline-flex items-center gap-2">
                <StarRating value={service?.averageRating} />
                <span>{service?.averageRating?.toFixed(1)}</span>
              </span>
            </DetailRow>
          ) : null}
        </dl>
      </Card>

      {/*
        Booking form — only rendered once the service is loaded (we have a real id).
        The component handles its own auth gate (logged-out → login CTA) and the
        create-booking mutation + redirect to /bookings. It renders as its own
        island below the article so the form reads as a distinct action area.
      */}
      {service?.id !== undefined ? (
        <BookingForm serviceId={service.id} />
      ) : null}

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
    <Container width="default">
      <p className="mb-6">
        <Link
          href="/"
          className={buttonClasses({ variant: "ghost", size: "sm" })}
        >
          ← Back to home services
        </Link>
      </p>
      <div className="space-y-6">{children}</div>
    </Container>
  );
}
