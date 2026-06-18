"use client";

/**
 * Inline "Leave a review" form for a COMPLETED booking.
 *
 * Rendered inside a customer's <BookingCard> when `booking.status === "COMPLETED"`. The
 * flow:
 *   - Collapsed: a "Leave a review" button.
 *   - Expanded: a 1–5 star rating (button group, keyboard-reachable) + an optional
 *     comment textarea (max 2000) + Submit/Cancel.
 *   - On 201 success: collapse to a small "Reviewed — thank you!" confirmation.
 *
 * EDGE — "already reviewed": there is NO "already reviewed" flag on BookingResponse, so
 * we can't pre-know whether the customer has already reviewed this booking. The strategy
 * is optimistic: show the affordance on every COMPLETED booking, and if the server
 * replies 422 ("already reviewed") we treat that as success — a review already exists,
 * which is the outcome the customer wanted — and settle into the "Reviewed" state. A
 * genuine 400/404 surfaces as an inline error the customer can retry.
 *
 * Validation mirrors the backend (ReviewCreateRequest): rating integer 1–5 required;
 * comment optional, ≤ 2000 chars. Client-side checks give instant feedback and avoid a
 * wasted round-trip; the server remains the source of truth.
 *
 * The mutation (useCreateReview) invalidates the reviews family + the customer bookings
 * family on success, so the service-detail reviews list and "My bookings" both refresh.
 *
 * Visual (Phase 7): expanded form is a tinted sub-island inside the booking card. Stars
 * reuse the StarRating amber palette so the picker matches the read-only display.
 * Submit/Cancel use the Button primitive.
 */
import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { useCreateReview } from "@/lib/api/reviews-queries";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";

/** Backend constraint mirrored client-side for instant feedback. */
const COMMENT_MAX = 2000;

interface ReviewFormProps {
  /** The COMPLETED booking being reviewed. */
  bookingId: number;
}

export function ReviewForm({ bookingId }: ReviewFormProps) {
  const createMutation = useCreateReview();

  // Three visual states: collapsed button → expanded form → settled confirmation.
  const [open, setOpen] = useState(false);
  const [done, setDone] = useState(false);

  // Form fields. `rating` is null until the customer picks; the star buttons set it.
  const [rating, setRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");

  const [error, setError] = useState<string | null>(null);

  // Once settled (success OR "already reviewed"), the form stays collapsed with a thank-
  // you — re-opening isn't useful since the review is filed.
  if (done) {
    return (
      <p className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-success">
        Reviewed — thank you!
      </p>
    );
  }

  if (!open) {
    return (
      <div className="mt-4">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => setOpen(true)}
        >
          Leave a review
        </Button>
      </div>
    );
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    // Client-side validation (mirrors ReviewCreateRequest). rating is required 1–5.
    if (rating === null || rating < 1 || rating > 5) {
      setError("Please choose a star rating.");
      return;
    }
    if (comment.length > COMMENT_MAX) {
      setError(`Comment must be at most ${COMMENT_MAX} characters.`);
      return;
    }

    createMutation.mutate(
      {
        bookingId,
        rating,
        // Send comment only when non-empty — the schema marks it optional, so omitting
        // keeps the payload clean and lets the backend store NULL vs "" distinctly.
        comment: comment.trim() || undefined,
      },
      {
        onSuccess: () => {
          setDone(true);
          setOpen(false);
        },
        onError: (err: unknown) => {
          // 422 "already reviewed" → treat as done: a review already exists, which is the
          // outcome the customer wanted. Settle into the confirmation state.
          if (err instanceof ApiError && err.status === 422) {
            setDone(true);
            setOpen(false);
            return;
          }
          setError(
            err instanceof ApiError
              ? err.message
              : "Couldn't submit your review. Please try again.",
          );
        },
      },
    );
  }

  const submitting = createMutation.isPending;

  return (
    <form
      onSubmit={handleSubmit}
      className="mt-4 rounded-2xl border border-border/60 bg-muted/50 p-4"
      aria-label="Leave a review"
    >
      {/*
        Star rating as a button group. Buttons (not a <select>) so the chosen rating is
        visible at a glance and each star is keyboard-focusable/activatable as a real
        button. aria-label communicates the value to assistive tech.
      */}
      <div>
        <span className="mb-1.5 block text-sm font-medium text-foreground">
          Your rating<span className="ml-0.5 text-danger">*</span>
        </span>
        <div className="flex gap-1" role="group" aria-label="Star rating">
          {[1, 2, 3, 4, 5].map((star) => {
            const active = rating !== null && star <= rating;
            return (
              <button
                key={star}
                type="button"
                aria-label={`${star} star${star === 1 ? "" : "s"}`}
                aria-pressed={active}
                disabled={submitting}
                onClick={() => setRating(star)}
                className={
                  active
                    ? "text-2xl leading-none text-amber-500 transition-transform hover:scale-110"
                    : "text-2xl leading-none text-muted-foreground/50 transition-colors hover:text-amber-400"
                }
              >
                ★
              </button>
            );
          })}
        </div>
      </div>

      <label className="mt-3 block">
        <span className="mb-1.5 block text-sm font-medium text-foreground">
          Comment <span className="font-normal text-muted-foreground">(optional)</span>
        </span>
        <Textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={COMMENT_MAX}
          rows={3}
          disabled={submitting}
          placeholder="How was your experience?"
        />
      </label>

      {error ? (
        <p className="mt-2 text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}

      <div className="mt-3 flex flex-wrap gap-3">
        <Button type="submit" size="sm" isLoading={submitting}>
          {submitting ? "Submitting…" : "Submit review"}
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => {
            setOpen(false);
            setError(null);
          }}
          disabled={submitting}
        >
          Cancel
        </Button>
      </div>
    </form>
  );
}
