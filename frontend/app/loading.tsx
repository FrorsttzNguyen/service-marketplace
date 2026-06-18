import { CatalogSkeleton } from "@/components/skeletons";

/**
 * Route-level `loading.tsx`.
 *
 * While a route segment is being streamed from the server (or suspended), Next
 * swaps in the nearest `loading.tsx` so the user gets immediate feedback instead
 * of a blank frame. At the root segment, this covers the initial server render
 * of the app shell before the client island hydrates.
 *
 * We reuse the catalog skeleton rather than inventing a new shape: it matches
 * the home page layout (which is the default landing route), so the handoff from
 * "loading" → "catalog" has no layout shift. Other routes (login, checkout) have
 * their own skeleton states inside their client islands for in-flight fetches;
 * this file is just the top-of-stream fallback.
 */
export default function Loading() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-8">
        {/* Title placeholder mirrors the home page's <h1> height so the layout
            doesn't jump once the real heading renders. */}
        <div className="h-9 w-64 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
        <div className="mt-2 h-4 w-72 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800" />
      </header>
      <CatalogSkeleton />
    </main>
  );
}
