"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Container } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { Button, buttonClasses } from "@/components/ui/button";

/**
 * Route-level error boundary (`error.tsx`).
 *
 * In the App Router, an unhandled error thrown during a route's render streams
 * the nearest `error.tsx` as a fallback INSTEAD of the broken subtree. This file
 * is that boundary for the whole app (it sits at the root segment).
 *
 * MUST be a client component — Next passes `{ error, reset }` as props, and
 * `reset()` is a client-side function that re-renders the error boundary's
 * segment, effectively retrying whatever threw.
 *
 * We deliberately:
 *   - Do NOT surface `error.message` / stack to the user. Most throw messages
 *     are developer-facing (e.g. a key name) and would leak implementation
 *     detail or confuse non-technical users. Errors are console.error'd for dev.
 *   - Offer TWO recovery paths:
 *       • "Try again"  → reset() (re-runs the failing segment; many transient
 *         errors — a suspended boundary, a one-off render hiccup — clear on retry).
 *       • "Back to catalog" → safe navigation to a known-good route.
 *   - Keep the visual tone calm: a neutral island card (not the danger-tinted
 *     treatment used for data fetch failures), because a render error is
 *     genuinely unexpected and the red treatment would imply a known/expected
 *     failure mode.
 */
interface ErrorBoundaryProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalErrorBoundary({ error, reset }: ErrorBoundaryProps) {
  // Log once per error instance so developers can see what blew up in the
  // console without the user ever seeing it. The `digest` is Next's stable
  // error id — useful when matching production errors to telemetry.
  useEffect(() => {
    // eslint-disable-next-line no-console
    console.error("[route error boundary]", error);
  }, [error]);

  return (
    <Container width="narrow" className="max-w-xl py-16">
      <Card padded role="alert">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Something went wrong
        </h1>
        <p className="mt-2 text-muted-foreground">
          An unexpected error occurred while loading this page. You can try
          again, or head back to the catalog.
        </p>

        {/* `digest` is the only error detail we consider safe to surface: it's a
            server-generated hash, not a message or stack. Showing it gives users a
            reference to quote if they report the issue. */}
        {error?.digest ? (
          <p className="mt-3 text-xs text-muted-foreground">
            Reference: <span className="font-mono">{error.digest}</span>
          </p>
        ) : null}

        <div className="mt-6 flex flex-wrap gap-3">
          <Button onClick={() => reset()}>Try again</Button>
          <Link href="/" className={buttonClasses({ variant: "ghost" })}>
            Back to catalog
          </Link>
        </div>
      </Card>
    </Container>
  );
}
