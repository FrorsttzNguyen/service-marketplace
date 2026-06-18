/**
 * `cn` — tiny className combiner used across the UI primitives.
 *
 * Two jobs:
 *   1. `clsx` flattens any mix of strings / arrays / {className: bool} into a
 *      single space-separated string, dropping falsy values.
 *   2. `tailwind-merge` resolves CONFLICTING Tailwind utility classes so the
 *      LAST one wins (e.g. `cn("px-2", "px-4")` → `"px-4"`). Without it, both
 *      classes would ship to the DOM and CSS source-order would decide — which
 *      is fragile and order-dependent.
 *
 * Usage: `cn("base classes", conditional && "extra", className)` so callers can
 * override a primitive's defaults via its `className` prop and have it win.
 */
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
