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
 *
 * Visual (Phase 7): polished toggle pills. Active chip = filled primary with a soft
 * shadow; inactive = subtle ghost that fills with the accent tint on hover. Same
 * shape as the Button primitive for a consistent pill vocabulary.
 */
import { type ReactNode } from "react";
import type { Category } from "@/lib/api/services";
import { cn } from "@/lib/utils";

interface CategoryFilterProps {
  categories: Category[];
  /** Currently selected category id, or null/undefined for "All". */
  selectedCategoryId?: number | null;
  onSelect: (categoryId: number | null) => void;
  /** When true, the chips are disabled (e.g. parent is loading). */
  disabled?: boolean;
}

/** Shared classes for a chip; `active` toggles the filled vs ghost style. */
function chipClasses(active: boolean): string {
  const base =
    "rounded-pill px-4 py-1.5 text-sm font-medium transition-all disabled:cursor-not-allowed disabled:opacity-50";
  return active
    ? cn(base, "bg-primary text-primary-foreground shadow-island")
    : cn(
        base,
        "bg-card text-muted-foreground border border-border/60 hover:border-primary/40 hover:bg-accent-soft hover:text-primary",
      );
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
      <ChipButton
        onClick={() => onSelect(null)}
        disabled={disabled}
        pressed={selectedCategoryId === null || selectedCategoryId === undefined}
      >
        All
      </ChipButton>
      {categories.map((category) => (
        <ChipButton
          key={category.id}
          onClick={() => onSelect(category.id)}
          disabled={disabled}
          pressed={selectedCategoryId === category.id}
        >
          {category.name}
        </ChipButton>
      ))}
    </div>
  );
}

/** Single chip button. Extracted so the active class is derived in one place. */
function ChipButton({
  pressed,
  disabled,
  onClick,
  children,
}: {
  pressed: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={pressed}
      className={chipClasses(pressed)}
    >
      {children}
    </button>
  );
}
