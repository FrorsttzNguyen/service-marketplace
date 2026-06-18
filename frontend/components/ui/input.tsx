/**
 * Input / Textarea / Select — styled form controls that match the rounded,
 * soft design language.
 *
 * Shared base classes (`fieldBase`): rounded-2xl corners (softer than the old
 * `rounded`), a faint border, a subtle muted background, comfortable padding,
 * and a focus ring using the primary token. The focus ring here is the FILL
 * state (border brightens to primary); the global `:focus-visible` outline in
 * globals.css still covers keyboard users for the rare control that doesn't
 * match `:focus` (e.g. autofilled inputs).
 *
 * All three forward EVERY native prop (`value`, `onChange`, `id`, `type`,
 * `aria-*`, `disabled`, `autoComplete`, …) so call sites keep their existing
 * controlled-input wiring byte-for-byte. We only restyle.
 */
import { forwardRef, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

const fieldBase =
  "w-full rounded-2xl border border-border bg-card px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground/70 transition-colors hover:border-primary/40 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:cursor-not-allowed disabled:opacity-60";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, type = "text", ...rest }, ref) {
    // datetime-local / number inputs need slightly less horizontal trim on the
    // indicator; the base handles it. type is overridable by the caller.
    return (
      <input
        ref={ref}
        type={type}
        className={cn(fieldBase, className)}
        {...rest}
      />
    );
  },
);

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className, rows = 3, ...rest }, ref) {
  return (
    <textarea
      ref={ref}
      rows={rows}
      className={cn(fieldBase, "resize-y leading-relaxed", className)}
      {...rest}
    />
  );
});

export const Select = forwardRef<
  HTMLSelectElement,
  SelectHTMLAttributes<HTMLSelectElement>
>(function Select({ className, children, style, ...rest }, ref) {
  return (
    <select
      ref={ref}
      className={cn(
        fieldBase,
        // Hide the native arrow so our custom SVG (set via inline style below) shows.
        // appearance-none + the bg-* utilities position the custom chevron.
        "cursor-pointer appearance-none bg-no-repeat pr-10",
        className,
      )}
      style={{
        // Inline SVG chevron as a data URI so we don't ship an asset. Encoded
        // (%23 for #, %20 for space) so it survives the data: URL parsing. Kept
        // in `style` (not a Tailwind arbitrary value) because the value contains
        // quotes that break the bg-[…] class syntax under Next's CSS pipeline.
        backgroundImage:
          "url(\"data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20fill='none'%20viewBox='0%200%2024%2024'%20stroke='%236b7280'%20stroke-width='2'%3E%3Cpath%20stroke-linecap='round'%20stroke-linejoin='round'%20d='M19%209l-7%207-7-7'/%3E%3C/svg%3E\")",
        backgroundSize: "1.25rem",
        backgroundPosition: "right 0.75rem center",
        ...style,
      }}
      {...rest}
    >
      {children}
    </select>
  );
});
