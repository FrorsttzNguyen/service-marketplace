"use client";

/**
 * Service card — a single catalog tile linking to the detail page.
 *
 * Presentational + client (uses next/link, which is fine in server components too, but
 * keeping all catalog UI client for consistency since the list page is a client island).
 * Pinned to the generated `Service` type so a schema field rename surfaces here on the
 * next `gen:api`.
 *
 * Visual (Phase 7): each card is a rounded "island" with a soft shadow that lifts on
 * hover. Title + vendor + city + price are laid out with comfortable spacing; the
 * optional StarRating uses `averageRating` when present.
 */
import Link from "next/link";
import type { Service } from "@/lib/api/services";
import { StarRating } from "@/components/ui/star-rating";

/** Format a price + pricing type into a single readable label. */
function formatPrice(service: Service): string | null {
  const price = service.basePrice;
  const type = service.pricingType;
  if (price === undefined || price === null) {
    return type ? prettyPricingType(type) : null;
  }
  const formatted = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(price);
  const suffix =
    type === "HOURLY" ? "/hr" : type === "VARIABLE" ? " from" : "";
  return type === "VARIABLE" ? `${suffix} ${formatted}` : `${formatted}${suffix}`;
}

/** Human-readable label for the pricing-type enum. */
function prettyPricingType(type: NonNullable<Service["pricingType"]>): string {
  switch (type) {
    case "FIXED":
      return "Fixed price";
    case "HOURLY":
      return "Hourly";
    case "VARIABLE":
      return "Variable";
    default:
      return type;
  }
}

export function ServiceCard({ service }: { service: Service }) {
  const price = formatPrice(service);
  // averageRating may be undefined (unrated). Show stars only when we have one.
  const hasRating =
    typeof service.averageRating === "number" && service.averageRating > 0;

  return (
    <Link
      href={`/services/${service.id}`}
      className="group flex h-full flex-col rounded-2xl border border-border/60 bg-card p-5 shadow-island transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-island-hover"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-semibold leading-tight tracking-tight text-foreground transition-colors group-hover:text-primary">
          {service.title ?? "Untitled service"}
        </h3>
        {price ? (
          <span className="shrink-0 rounded-pill bg-accent-soft px-2.5 py-1 text-sm font-semibold text-primary">
            {price}
          </span>
        ) : null}
      </div>

      {service.vendorName ? (
        <p className="mt-1.5 text-sm text-muted-foreground">
          by {service.vendorName}
        </p>
      ) : null}

      {/* Filler grows so the bottom meta row sits at a consistent height
          across cards in the same row, regardless of description length. */}
      <div className="mt-3 flex-1" />

      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-sm">
        {hasRating ? (
          <StarRating value={service.averageRating} />
        ) : null}
        {service.city ? (
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            <span aria-hidden="true">📍</span>
            {service.city}
          </span>
        ) : null}
        {service.categoryName ? (
          <span className="ml-auto rounded-pill bg-muted px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {service.categoryName}
          </span>
        ) : null}
      </div>
    </Link>
  );
}
