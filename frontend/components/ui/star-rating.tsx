/**
 * StarRating — read-only star display for review cards / service ratings.
 *
 * Renders ★/☆ glyphs (no icon dependency — keeps the bundle unchanged). The
 * filled stars are amber; empty are muted. `aria-label` communicates the value
 * to assistive tech, and `role="img"` marks the whole cluster as one image so
 * screen readers don't announce five separate characters.
 *
 * This is the READ-ONLY version used in reviews-section and on service cards.
 * The interactive picker in review-form keeps its own button group (it needs
 * clickable per-star buttons) but reuses this file's visual sizing via the
 * exported `starGlyph` helper.
 */
import { cn } from "@/lib/utils";

interface StarRatingProps {
  /** Rating 1–5. Fractions are rounded so 4.4 → 4 stars. */
  value?: number;
  /** Max stars (default 5). Kept configurable though the app is always /5. */
  max?: number;
  className?: string;
  /** Show the numeric value after the stars (e.g. "★★★★☆ 4.0"). */
  showValue?: boolean;
}

/** Round to nearest whole star, clamped to [0, max]. */
function roundStar(value: number | undefined, max: number): number {
  if (typeof value !== "number" || Number.isNaN(value)) return 0;
  return Math.min(max, Math.max(0, Math.round(value)));
}

export function StarRating({
  value,
  max = 5,
  className,
  showValue = false,
}: StarRatingProps) {
  const filled = roundStar(value, max);
  const ariaLabel =
    typeof value === "number"
      ? `${value.toFixed(1)} out of ${max}`
      : "unrated";

  return (
    <span
      role="img"
      aria-label={ariaLabel}
      className={cn("inline-flex items-center gap-1 text-amber-500", className)}
    >
      <span aria-hidden="true" className="tracking-tight">
        {"★".repeat(filled)}
        <span className="text-muted-foreground/50">
          {"☆".repeat(Math.max(0, max - filled))}
        </span>
      </span>
      {showValue && typeof value === "number" ? (
        <span className="text-xs font-medium text-muted-foreground">
          {value.toFixed(1)}
        </span>
      ) : null}
    </span>
  );
}
