/**
 * Container / PageHeader — consistent page chrome.
 *
 * Most pages previously hand-wrote `mx-auto max-w-3xl px-4 py-10`. These two
 * primitives standardize that, plus give page headers a consistent title +
 * subtitle + actions layout. Centralizing it means a future change to the page
 * gutter or max width lands in one place.
 */
import { type ReactNode } from "react";
import { cn } from "@/lib/utils";

type Width = "narrow" | "checkout" | "default" | "wide";

const WIDTH_CLASSES: Record<Width, string> = {
  narrow: "max-w-sm", // login / register
  checkout: "max-w-2xl", // checkout (order summary + payment form)
  default: "max-w-3xl", // catalog, bookings, lists
  wide: "max-w-5xl", // admin tables (kept for parity, unused for now)
};

interface ContainerProps {
  width?: Width;
  className?: string;
  children: ReactNode;
  /** Render as a different element (e.g. `<main>`). Defaults to `<main>`. */
  as?: "main" | "div" | "section" | "article";
}

export function Container({
  width = "default",
  className,
  children,
  as: As = "main",
}: ContainerProps) {
  return (
    <As
      className={cn(
        "mx-auto w-full px-4 py-10 sm:px-6",
        WIDTH_CLASSES[width],
        className,
      )}
    >
      {children}
    </As>
  );
}

interface PageHeaderProps {
  /** Optional small kicker label above the title (e.g. "404"). */
  eyebrow?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  /** Optional right-aligned actions (e.g. "+ New service" button). */
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({
  eyebrow,
  title,
  subtitle,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        "mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between",
        className,
      )}
    >
      <div>
        {eyebrow ? (
          <p className="text-xs font-semibold uppercase tracking-wider text-primary">
            {eyebrow}
          </p>
        ) : null}
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-foreground">
          {title}
        </h1>
        {subtitle ? (
          <p className="mt-1.5 text-muted-foreground">{subtitle}</p>
        ) : null}
      </div>
      {actions ? (
        <div className="flex flex-wrap items-center gap-3">{actions}</div>
      ) : null}
    </header>
  );
}
