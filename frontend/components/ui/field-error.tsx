/**
 * FieldError — inline validation message under a form field.
 *
 * Centralizes the (previously repeated) `text-sm text-red-600 dark:text-red-400`
 * error paragraph. `role="alert"` so screen readers announce it when it appears.
 * Renders nothing when there's no message, so callers can always mount it
 * conditionally on the error string.
 */
import { type ReactNode } from "react";
import { cn } from "@/lib/utils";

interface FieldErrorProps {
  id?: string;
  className?: string;
  children?: ReactNode;
}

export function FieldError({ id, className, children }: FieldErrorProps) {
  if (!children) return null;
  return (
    <p
      id={id}
      role="alert"
      className={cn("mt-1.5 text-sm text-danger", className)}
    >
      {children}
    </p>
  );
}
