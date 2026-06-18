"use client";

/**
 * Reviews section — public list of reviews for a service.
 *
 * Rendered on the service detail page below the article. Uses `useServiceReviews`,
 * which hits the PUBLIC `GET /api/reviews/service/{serviceId}` — so this section works
 * for logged-out visitors too (no <RequireAuth> here). The backend returns reviews
 * newest-first; we render them in that order.
 *
 * States mirror the rest of the app's data-view trio:
 *   - loading → a small inline placeholder (the section is secondary to the service
 *     details, so we don't use the heavier CatalogSkeleton);
 *   - error   → reuse <ErrorState> with a retry (public endpoint, but a CORS/network
 *               failure still deserves a recovery path);
 *   - empty   → "No reviews yet." (a brand-new or unreviewed service);
 *   - data    → a list of review cards (stars, customer name, comment, date).
 *
 * Note: the service's own `averageRating`/`totalReviews` (shown in the detail <dl>) come
 * from the service row, not from this list — they're a separate aggregate and may lag
 * (the catalog mapper currently reports totalReviews as 0). Don't try to reconcile them
 * here; this list is the source of truth for what's actually been written.
 *
 * Visual (Phase 7): the section is its own island; each review is a sub-island card
 * using the StarRating primitive (no more hand-rolled ★/☆ strings).
 */
import { useServiceReviews } from "@/lib/api/reviews-queries";
import type { Review } from "@/lib/api/reviews";
import { ErrorState } from "@/components/error-state";
import { Card } from "@/components/ui/card";
import { StarRating } from "@/components/ui/star-rating";

interface ReviewsSectionProps {
  serviceId: number;
}

/** Format a date-time ISO string as a readable local date, or "—" if missing/invalid. */
function formatDate(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString(undefined, { dateStyle: "medium" });
}

function ReviewCard({ review }: { review: Review }) {
  // rating may be absent on a malformed row; StarRating handles undefined as 0/unrated.
  const rating = review.rating;
  return (
    <Card as="li" padded className="py-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <StarRating value={rating} />
          <span className="text-sm font-semibold text-foreground">
            {review.customerName ?? "Anonymous customer"}
          </span>
        </div>
        <time className="text-xs text-muted-foreground">
          {formatDate(review.createdAt)}
        </time>
      </div>
      {review.comment ? (
        <p className="mt-3 text-sm leading-relaxed text-foreground/90">
          {review.comment}
        </p>
      ) : null}
    </Card>
  );
}

export function ReviewsSection({ serviceId }: ReviewsSectionProps) {
  const { data, isPending, isError, error, refetch } =
    useServiceReviews(serviceId);

  const reviews = data ?? [];

  return (
    <section className="mt-2" aria-label="Reviews">
      <h2 className="mb-4 text-xl font-bold tracking-tight text-foreground">
        Reviews{" "}
        {reviews.length > 0 ? (
          <span className="text-muted-foreground">({reviews.length})</span>
        ) : null}
      </h2>

      {isPending ? (
        // Lightweight placeholder — the section is secondary, so a couple of card-shaped
        // pulse lines are enough (not the full CatalogSkeleton).
        <div aria-busy="true" aria-label="Loading reviews">
          <div className="space-y-3">
            {[0, 1].map((i) => (
              <Card key={i} padded className="py-5">
                <div className="h-4 w-1/3 animate-pulse rounded-pill bg-muted" />
                <div className="mt-3 h-3 w-full animate-pulse rounded-pill bg-muted" />
              </Card>
            ))}
          </div>
        </div>
      ) : isError ? (
        <ErrorState
          error={error}
          onRetry={() => refetch()}
          title="Couldn't load reviews."
        />
      ) : reviews.length === 0 ? (
        <Card padded className="py-8 text-center text-sm text-muted-foreground">
          No reviews yet.
        </Card>
      ) : (
        <ul className="space-y-3">
          {reviews.map((review) => (
            <ReviewCard key={review.id ?? Math.random()} review={review} />
          ))}
        </ul>
      )}
    </section>
  );
}
