"use client";

/**
 * Category filter chips.
 *
 * Renders an "All" chip plus one chip per derived category. Controlled by the parent
 * (it owns `selectedCategoryId` and the `onSelect` callback), so this component stays
 * presentational and stateless — easy to test and reuse.
 *
 * Categories are NOT fetched here. The parent passes the derived list (from
 * `useCatalogCategories` → `deriveCategories`, which reads them off loaded service
 * rows). See services.ts: there is intentionally no `GET /api/categories` call.
 */
import type { Category } from "@/lib/api/services";

interface CategoryFilterProps {
  categories: Category[];
  /** Currently selected category id, or null/undefined for "All". */
  selectedCategoryId?: number | null;
  onSelect: (categoryId: number | null) => void;
  /** When true, the chips are disabled (e.g. parent is loading). */
  disabled?: boolean;
}

/** Shared classes for a chip; `active` toggles the filled vs outlined style. */
function chipClasses(active: boolean, disabled: boolean): string {
  const base =
    "rounded-full border px-3 py-1 text-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed";
  return active
    ? `${base} border-blue-600 bg-blue-600 text-white`
    : `${base} border-neutral-300 text-neutral-700 hover:border-blue-400 hover:text-blue-600 dark:border-neutral-700 dark:text-neutral-300`;
}

export function CategoryFilter({
  categories,
  selectedCategoryId,
  onSelect,
  disabled = false,
}: CategoryFilterProps) {
  if (categories.length === 0) {
    // No categories yet (empty catalog or still deriving) — render nothing rather
    // than a lone "All" chip with nothing to switch to.
    return null;
  }

  return (
    <div
      className="flex flex-wrap gap-2"
      role="group"
      aria-label="Filter by category"
    >
      <button
        type="button"
        onClick={() => onSelect(null)}
        disabled={disabled}
        aria-pressed={selectedCategoryId === null || selectedCategoryId === undefined}
        className={chipClasses(
          selectedCategoryId === null || selectedCategoryId === undefined,
          disabled,
        )}
      >
        All
      </button>
      {categories.map((category) => (
        <button
          key={category.id}
          type="button"
          onClick={() => onSelect(category.id)}
          disabled={disabled}
          aria-pressed={selectedCategoryId === category.id}
          className={chipClasses(selectedCategoryId === category.id, disabled)}
        >
          {category.name}
        </button>
      ))}
    </div>
  );
}
