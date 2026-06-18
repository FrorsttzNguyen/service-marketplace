"use client";

/**
 * Pagination controls driven by Spring's `Page<T>` metadata.
 *
 * Uses `first`/`last` to disable Prev/Next (more reliable than comparing `number`
 * against `totalPages`, which can be 0 for an empty result). The center shows
 * `number + 1` (human-friendly, 1-based) of `totalPages`. Resets to page 0 are the
 * parent's job (it owns page state); this component just emits page deltas.
 *
 * Visual (Phase 7): prev/next are ghost pill buttons; the page indicator is wrapped
 * in a subtle pill so the whole control reads as one rounded island of its own.
 */
import { Button } from "@/components/ui/button";

interface PaginationProps {
  /** Current page (0-based from Spring). */
  number?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
  /** Called with the new 0-based page number. */
  onPageChange: (page: number) => void;
  disabled?: boolean;
}

export function Pagination({
  number = 0,
  totalPages = 0,
  first = true,
  last = true,
  onPageChange,
  disabled = false,
}: PaginationProps) {
  // No controls when there's at most one page — pagination would be noise.
  if (totalPages <= 1) return null;

  const prevDisabled = disabled || first;
  const nextDisabled = disabled || last;

  return (
    <nav
      className="mt-8 flex items-center justify-center gap-3 text-sm"
      aria-label="Pagination"
    >
      <Button
        variant="ghost"
        size="sm"
        onClick={() => onPageChange(Math.max(0, number - 1))}
        disabled={prevDisabled}
      >
        ← Previous
      </Button>
      <span
        className="rounded-pill bg-card px-4 py-1.5 font-medium text-muted-foreground shadow-island"
        data-testid="page-info"
      >
        Page {number + 1} of {totalPages}
      </span>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => onPageChange(Math.min(totalPages - 1, number + 1))}
        disabled={nextDisabled}
      >
        Next →
      </Button>
    </nav>
  );
}
