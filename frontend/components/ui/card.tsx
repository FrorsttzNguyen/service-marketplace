/**
 * Card — the "island": an elevated, well-padded rounded surface floating on the
 * tinted page background. The defining primitive of this design language.
 *
 * Every major section (hero, service card, booking form, order summary, review
 * card, table panel, error/empty boxes) is a Card. The soft `shadow-island` +
 * `rounded-2xl` + bright `bg-card` is what makes content read as islands sitting
 * on the tinted `bg-background` wash.
 *
 * Optional `interactive` adds a hover lift (for linked service cards). Optional
 * `as` lets a Card render as a different element (e.g. `<li>`, `<form>`) where the
 * surrounding semantics demand it. The component is POLYMORPHIC: when you pass
 * `as="form"`, TS accepts form-specific props like `onSubmit`; when `as="li"`, it
 * accepts list-item props. (Implemented via the standard
 * `ComponentPropsWithoutRef<E>` intersection pattern.)
 *
 * Subcomponents `CardHeader` / `CardTitle` / `CardDescription` / `CardBody` /
 * `CardFooter` give the common internal structure consistent spacing without
 * each caller re-inventing the paddings.
 */
import {
  type ComponentPropsWithoutRef,
  type ElementType,
  type ReactNode,
} from "react";
import { cn } from "@/lib/utils";

type CardProps<E extends ElementType> = {
  as?: E;
  /** Hover lift + accent-tinted shadow for clickable/linked cards. */
  interactive?: boolean;
  /** Standard padding inside the island (p-6 / sm:p-8). */
  padded?: boolean;
  className?: string;
  children?: ReactNode;
} & Omit<ComponentPropsWithoutRef<E>, "as" | "className">;

const CARD_BASE =
  "rounded-2xl bg-card text-card-foreground border border-border/60 shadow-island";

export function Card<E extends ElementType = "div">({
  as,
  interactive = false,
  padded = false,
  className,
  children,
  ...rest
}: CardProps<E>) {
  const Component = (as ?? "div") as ElementType;
  return (
    <Component
      className={cn(
        CARD_BASE,
        padded && "p-6 sm:p-8",
        interactive &&
          "transition-all duration-200 hover:-translate-y-0.5 hover:shadow-island-hover hover:border-primary/40",
        className,
      )}
      {...rest}
    >
      {children}
    </Component>
  );
}

export function CardHeader({
  className,
  children,
  ...rest
}: ComponentPropsWithoutRef<"div">) {
  return (
    <div
      className={cn("flex flex-col gap-1 p-6 sm:p-8 pb-0", className)}
      {...rest}
    >
      {children}
    </div>
  );
}

export function CardTitle({
  className,
  children,
  ...rest
}: ComponentPropsWithoutRef<"h3">) {
  return (
    <h3
      className={cn(
        "text-lg font-semibold leading-tight tracking-tight",
        className,
      )}
      {...rest}
    >
      {children}
    </h3>
  );
}

export function CardDescription({
  className,
  children,
  ...rest
}: ComponentPropsWithoutRef<"p">) {
  return (
    <p className={cn("text-sm text-muted-foreground", className)} {...rest}>
      {children}
    </p>
  );
}

export function CardBody({
  className,
  children,
  ...rest
}: ComponentPropsWithoutRef<"div">) {
  return (
    <div className={cn("p-6 sm:p-8 pt-4", className)} {...rest}>
      {children}
    </div>
  );
}

export function CardFooter({
  className,
  children,
  ...rest
}: ComponentPropsWithoutRef<"div">) {
  return (
    <div
      className={cn(
        "flex flex-wrap items-center gap-3 p-6 sm:p-8 pt-0",
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
