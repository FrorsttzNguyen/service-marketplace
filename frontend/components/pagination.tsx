"use client";

/**
 * Pagination controls driven by Spring's `Page<T>` metadata.
 *
 * Uses `first`/`last` to disable Prev/Next (more reliable than comparing `number`
 * against `totalPages`, which can be 0 for an empty result). The center shows
 * `number + 1` (human-friendly, 1-based) of `totalPages`. Resets to page 0 are the
 * parent's job (it owns page state); this component just emits page deltas.
 */
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
      className="mt-8 flex items-center justify-center gap-4 text-sm"
      aria-label="Pagination"
    >
      <button
        type="button"
        onClick={() => onPageChange(Math.max(0, number - 1))}
        disabled={prevDisabled}
        className="rounded border border-neutral-300 px-3 py-1.5 text-neutral-700 hover:border-blue-400 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-40 dark:border-neutral-700 dark:text-neutral-300"
      >
        ← Previous
      </button>
      <span className="text-neutral-600 dark:text-neutral-400" data-testid="page-info">
        Page {number + 1} of {totalPages}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(Math.min(totalPages - 1, number + 1))}
        disabled={nextDisabled}
        className="rounded border border-neutral-300 px-3 py-1.5 text-neutral-700 hover:border-blue-400 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-40 dark:border-neutral-700 dark:text-neutral-300"
      >
        Next →
      </button>
    </nav>
  );
}
