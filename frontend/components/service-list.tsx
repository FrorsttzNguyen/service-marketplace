"use client";

/**
 * Service list — renders one catalog page from a Spring `Page<Service>` as a
 * responsive grid of linked cards.
 *
 * Presentational + client. It only depends on the data shape (not React Query), so
 * it's reusable on both the home page and any future category-focused route, and easy
 * to unit-test in isolation later.
 *
 * Visual (Phase 7): the grid is the catalog's island stack. Empty state is a soft
 * dashed island so it reads as "this island happens to be empty" rather than a hard
 * error border.
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
      <p className="mb-4 text-sm text-muted-foreground">
        {total} service{total === 1 ? "" : "s"} available
      </p>
      {services.length === 0 ? (
        <p
          className="rounded-2xl border border-dashed border-border bg-card/50 p-8 text-center text-muted-foreground shadow-island"
          data-testid="empty-state"
        >
          No services match this filter. Try clearing the category filter or
          check back soon.
        </p>
      ) : (
        // Items-stretch so each card fills its grid cell height (the cards use
        // flex-col with a flex-1 spacer to align their bottom meta rows).
        <ul className="grid gap-4 sm:grid-cols-2 items-stretch">
          {services.map((service) => (
            <li key={service.id} className="h-full">
              <ServiceCard service={service} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
