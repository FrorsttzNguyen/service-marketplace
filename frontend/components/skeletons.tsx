/**
 * Loading skeletons for the catalog.
 *
 * Why skeletons (not just "Loading…"): a skeleton mirrors the final layout, so the
 * page doesn't jump when data arrives (reduces CLS) and gives the user an immediate
 * sense of "something is loading in this shape." The shimmer uses a subtle pulse
 * animation via Tailwind's `animate-pulse`.
 */

/** A grid of card-shaped placeholders matching the catalog layout. */
export function CatalogSkeleton({ count = 6 }: { count?: number }) {
  return (
    <section aria-busy="true" aria-label="Loading services">
      <div className="mb-4 h-4 w-40 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
      <ul className="grid gap-3 sm:grid-cols-2">
        {Array.from({ length: count }).map((_, i) => (
          <li
            key={i}
            className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800"
          >
            <div className="h-5 w-3/4 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
            <div className="mt-2 h-3 w-1/2 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
            <div className="mt-3 h-3 w-1/3 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Placeholder matching the detail page layout (image + meta + description). */
export function ServiceDetailSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading service details">
      <div className="mb-4 h-3 w-24 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
      <div className="mb-3 h-8 w-2/3 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
      <div className="mb-8 h-4 w-1/2 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
      <div className="mb-8 h-48 w-full animate-pulse rounded-lg bg-neutral-200 dark:bg-neutral-800" />
      <div className="space-y-2">
        <div className="h-4 w-full animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
        <div className="h-4 w-full animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
      </div>
    </div>
  );
}
