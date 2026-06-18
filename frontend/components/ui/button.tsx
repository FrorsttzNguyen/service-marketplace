/**
 * Button — the app's pill-shaped button primitive.
 *
 * Design language: rounded-full ("pill") buttons, soft diffuse shadows, vibrant
 * but tasteful indigo primary. Variants cover every button role in the app:
 *   - primary     → indigo solid (CTA: "Request booking", "Pay", "Log in")
 *   - secondary   → subtle filled island (alternate CTA, "Sign up" when paired)
 *   - ghost       → transparent, hovers to a tint (nav links, "Cancel")
 *   - destructive → red solid or outline ("Cancel booking", "Reject")
 *   - link        → inline link styling (rare; mostly we use <Button asChild>)
 *
 * `isLoading` shows a spinner + disables the button (replaces ad-hoc
 * "Booking…" / "Submitting…" labels; callers can keep their own label too).
 *
 * The component renders a native `<button>` so all the existing onClick /
 * disabled / type / aria wiring at call sites works unchanged. For the few call
 * sites that navigate (e.g. "Pay now" → /checkout), prefer a `<Link>` styled
 * as a button via the `buttonClasses()` export, since real navigation should
 * stay a real link for accessibility & middle-click.
 */
import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";
import { Spinner } from "./spinner";

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "ghost"
  | "destructive"
  | "destructiveOutline"
  | "success"
  | "link";
export type ButtonSize = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner and disables the button (in-flight mutation state). */
  isLoading?: boolean;
  /** Make the button take full width of its container (login/register CTAs). */
  fullWidth?: boolean;
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  // Indigo solid — the primary CTA. Pill shape, soft hover lift.
  primary:
    "bg-primary text-primary-foreground shadow-island hover:shadow-island-hover hover:brightness-110 active:brightness-95",
  // Subtle filled island — alternate CTA, reads as secondary.
  secondary:
    "bg-accent-strong text-primary shadow-island hover:bg-accent-strong/80 hover:shadow-island-hover",
  // Transparent — nav links, "Cancel". Hovers to a soft tint.
  ghost:
    "bg-transparent text-foreground hover:bg-accent-soft hover:text-primary",
  // Red solid — destructive CTA where red should dominate.
  destructive:
    "bg-danger text-danger-foreground shadow-island hover:brightness-110 active:brightness-95",
  // Red outline — destructive intent WITHOUT a heavy red fill ("Cancel booking").
  destructiveOutline:
    "bg-card text-danger border border-danger/40 hover:bg-danger/10",
  // Green solid — success-forward CTA ("Approve", "Activate").
  success:
    "bg-success text-success-foreground shadow-island hover:brightness-110 active:brightness-95",
  // Inline link styling (rare; kept for parity with the variant set).
  link: "bg-transparent text-primary underline-offset-4 hover:underline px-0 py-0",
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: "text-sm px-3 py-1.5 gap-1.5",
  md: "text-sm px-4 py-2.5 gap-2",
  lg: "text-base px-6 py-3 gap-2",
};

/**
 * Base classes shared by every variant. `inline-flex` + `items-center` keep the
 * optional spinner aligned with the label. The global `:focus-visible` ring in
 * globals.css handles keyboard focus, so we don't add a competing ring here.
 */
const BASE_CLASSES =
  "inline-flex items-center justify-center rounded-full font-medium transition-all duration-150 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:shadow-none disabled:hover:brightness-100";

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  function Button(
    {
      variant = "primary",
      size = "md",
      isLoading = false,
      fullWidth = false,
      disabled,
      className,
      children,
      type = "button",
      ...rest
    },
    ref,
  ) {
    const isDisabled = disabled || isLoading;
    return (
      <button
        ref={ref}
        type={type}
        disabled={isDisabled}
        data-loading={isLoading ? "" : undefined}
        className={cn(
          BASE_CLASSES,
          SIZE_CLASSES[size],
          VARIANT_CLASSES[variant],
          fullWidth && "w-full",
          className,
        )}
        {...rest}
      >
        {isLoading ? <Spinner size={size === "lg" ? 18 : 16} /> : null}
        {children}
      </button>
    );
  },
);

/**
 * The classes a Button would get, WITHOUT rendering a `<button>`. Use this to
 * style a `<Link>` (or any element) exactly like a Button — important for
 * navigation, which should stay a real anchor for accessibility and
 * middle-click-to-open support (e.g. "Pay now" → /checkout/<id>).
 */
export function buttonClasses({
  variant = "primary",
  size = "md",
  fullWidth = false,
  className,
}: {
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  className?: string;
} = {}): string {
  return cn(
    BASE_CLASSES,
    SIZE_CLASSES[size],
    VARIANT_CLASSES[variant],
    fullWidth && "w-full",
    className,
  );
}
