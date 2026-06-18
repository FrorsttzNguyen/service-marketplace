/**
 * Spinner — a small CSS-only loading indicator.
 *
 * Why CSS-only: no extra dependency, animates smoothly even before JS hydrates
 * (it's just a div + a Tailwind animation), and stays crisp at any size. We use
 * Tailwind's `animate-spin` (a built-in `@keyframes spin`) on a ring drawn with
 * `border` + transparent gap. `currentColor` makes it inherit the surrounding
 * text color so a Spinner inside a primary Button is white, inside a Card is
 * foreground, etc.
 *
 * The `size` prop maps to pixel sizes; keep the border thickness proportional.
 */
import { cn } from "@/lib/utils";

interface SpinnerProps {
  /** Diameter in pixels. Defaults to 16 (sits nicely inside a button). */
  size?: number;
  /** Extra stroke thickness for larger spinners. */
  thickness?: number;
  className?: string;
  /** Accessible label. When omitted, the spinner is `aria-hidden` (it's usually
   * paired with visible "Loading…" text). */
  "aria-label"?: string;
}

export function Spinner({
  size = 16,
  thickness = 2,
  className,
  "aria-label": ariaLabel,
}: SpinnerProps) {
  return (
    <span
      role={ariaLabel ? "status" : undefined}
      aria-label={ariaLabel}
      aria-hidden={ariaLabel ? undefined : true}
      className={cn(
        "inline-block animate-spin rounded-full",
        "border-current border-t-transparent",
        className,
      )}
      style={{
        width: `${size}px`,
        height: `${size}px`,
        borderWidth: `${thickness}px`,
      }}
    />
  );
}
