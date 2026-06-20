/**
 * Label — a consistent form field label.
 *
 * Wraps the `<label>` element so every form (login, register, booking, review,
 * provider service) gets the same label weight/spacing. `required` renders the
 * standard red asterisk so call sites don't each hand-roll `<span>*</span>`.
 *
 * Kept as a plain element (not forwarding `htmlFor` magic) — callers still pass
 * `htmlFor` explicitly to keep the label↔input pairing auditable per field.
 */
import { type LabelHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/utils";

interface LabelProps extends LabelHTMLAttributes<HTMLLabelElement> {
  /** Shows the red "*" required marker after the label text. */
  required?: boolean;
  /** Optional muted hint shown AFTER the label, e.g. "(optional)". */
  hint?: ReactNode;
  children?: ReactNode;
}

export function Label({
  required = false,
  hint,
  className,
  children,
  ...rest
}: LabelProps) {
  return (
    <label
      className={cn("mb-1.5 block text-sm font-medium text-foreground", className)}
      {...rest}
    >
      {children}
      {required ? <span className="ml-0.5 text-danger">*</span> : null}
      {hint ? (
        <span className="ml-1 font-normal text-muted-foreground">{hint}</span>
      ) : null}
    </label>
  );
}
