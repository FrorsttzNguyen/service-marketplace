import Link from "next/link";
import { Container } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { buttonClasses } from "@/components/ui/button";

/**
 * App Router `not-found.tsx` — the 404 page.
 *
 * Next renders this component whenever a route doesn't match (or a page throws
 * the special `notFound()`). It's a SERVER component, so it can't use hooks —
 * just static, friendly markup. We deliberately mirror the rest of the app's
 * visual language (max-w container, soft island, primary accent) so a 404 reads
 * as part of the product, not a generic Next.js error page.
 *
 * Why no image / illustration: the project intentionally ships no external
 * assets, and a styled-text 404 stays light, accessible, and on-brand.
 */
export default function NotFound() {
  return (
    <Container width="default" className="max-w-2xl py-20 text-center">
      <Card padded className="py-12">
        <p className="text-sm font-semibold uppercase tracking-wider text-primary">
          404
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-foreground">
          We can&apos;t find that page
        </h1>
        <p className="mx-auto mt-3 max-w-md text-muted-foreground">
          The link may be broken, or the page may have been moved or removed.
          Browse the catalog to get back on track.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/"
            className={buttonClasses({ variant: "primary", size: "md" })}
          >
            Browse services
          </Link>
          <Link
            href="/bookings"
            className={buttonClasses({ variant: "ghost", size: "md" })}
          >
            My bookings
          </Link>
        </div>
      </Card>
    </Container>
  );
}
