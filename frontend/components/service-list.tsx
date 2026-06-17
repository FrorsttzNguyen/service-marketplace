"use client";

/**
 * Service list — renders one catalog page from a Spring `Page<Service>`.
 *
 * Kept as a presentational client component so the home page stays readable and
 * this list is reusable on a future category page (Slice 1). It only depends on
 * the data shape, not on React Query, so it's easy to unit-test in isolation later.
 */
import type { ServicePage } from "@/lib/api/services";

export function ServiceList({ page }: { page: ServicePage }) {
  // `content` is optional in the generated schema (Spring always sends it, but TS
  // can't know that), so default to an empty array for safety.
  const services = page.content ?? [];
  const total = page.totalElements ?? 0;

  return (
    <section>
      <p className="mb-4 text-sm text-neutral-600 dark:text-neutral-400">
        {total} service{total === 1 ? "" : "s"} available
      </p>
      {services.length === 0 ? (
        <p
          className="rounded border border-dashed border-neutral-300 p-6 text-center text-neutral-500 dark:border-neutral-700 dark:text-neutral-400"
          data-testid="empty-state"
        >
          No services have been listed yet. Check back soon.
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {services.map((service) => (
            <li
              key={service.id}
              className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800"
            >
              <h3 className="font-semibold">{service.title ?? "Untitled service"}</h3>
              {service.vendorName ? (
                <p className="text-sm text-neutral-500 dark:text-neutral-400">
                  by {service.vendorName}
                </p>
              ) : null}
              {service.city ? (
                <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">
                  📍 {service.city}
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
