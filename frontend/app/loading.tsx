import { CatalogSkeleton } from "@/components/skeletons";
import { Container } from "@/components/ui/container";

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
    <Container width="default">
      <header className="mb-8">
        {/* Title placeholder mirrors the home page's hero <h1> height so the layout
            doesn't jump once the real heading renders. */}
        <div className="h-9 w-64 animate-pulse rounded-pill bg-muted" />
        <div className="mt-2 h-4 w-72 animate-pulse rounded-pill bg-muted" />
      </header>
      <CatalogSkeleton />
    </Container>
  );
}
