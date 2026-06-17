"use client";

/**
 * Service list — renders one catalog page from a Spring `Page<Service>` as a
 * responsive grid of linked cards.
 *
 * Presentational + client. It only depends on the data shape (not React Query), so
 * it's reusable on both the home page and any future category-focused route, and easy
 * to unit-test in isolation later.
 */
import type { ServicePage } from "@/lib/api/services";
import { ServiceCard } from "./service-card";

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
          No services match this filter. Try clearing the category filter or check
          back soon.
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {services.map((service) => (
            <li key={service.id}>
              <ServiceCard service={service} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
