"use client";

/**
 * Service card — a single catalog tile linking to the detail page.
 *
 * Presentational + client (uses next/link, which is fine in server components too, but
 * keeping all catalog UI client for consistency since the list page is a client island).
 * Pinned to the generated `Service` type so a schema field rename surfaces here on the
 * next `gen:api`.
 */
import Link from "next/link";
import type { Service } from "@/lib/api/services";

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

  return (
    <Link
      href={`/services/${service.id}`}
      className="group block rounded-lg border border-neutral-200 p-4 transition-colors hover:border-blue-400 hover:shadow-sm dark:border-neutral-800 dark:hover:border-blue-500"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-semibold group-hover:text-blue-600 dark:group-hover:text-blue-400">
          {service.title ?? "Untitled service"}
        </h3>
        {price ? (
          <span className="shrink-0 text-sm font-medium text-neutral-700 dark:text-neutral-300">
            {price}
          </span>
        ) : null}
      </div>
      {service.vendorName ? (
        <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">
          by {service.vendorName}
        </p>
      ) : null}
      {service.categoryName ? (
        <p className="mt-1 text-xs uppercase tracking-wide text-neutral-400 dark:text-neutral-500">
          {service.categoryName}
        </p>
      ) : null}
      {service.city ? (
        <p className="mt-2 text-sm text-neutral-500 dark:text-neutral-400">
          📍 {service.city}
        </p>
      ) : null}
    </Link>
  );
}
