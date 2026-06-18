"use client";

import Link from "next/link";
import { useEffect } from "react";

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
 *   - Keep the visual tone calm: neutral card, not the red alarm used for data
 *     fetch failures, because a render error is genuinely unexpected and the
 *     red treatment would imply a known/expected failure mode.
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
    <main className="mx-auto max-w-xl px-4 py-16">
      <div
        className="rounded-lg border border-neutral-200 bg-white p-8 dark:border-neutral-800 dark:bg-neutral-950"
        role="alert"
      >
        <h1 className="text-2xl font-bold tracking-tight">
          Something went wrong
        </h1>
        <p className="mt-2 text-neutral-600 dark:text-neutral-400">
          An unexpected error occurred while loading this page. You can try
          again, or head back to the catalog.
        </p>

        {/* `digest` is the only error detail we consider safe to surface: it's a
            server-generated hash, not a message or stack. Showing it gives users a
            reference to quote if they report the issue. */}
        {error?.digest ? (
          <p className="mt-3 text-xs text-neutral-400">
            Reference: <span className="font-mono">{error.digest}</span>
          </p>
        ) : null}

        <div className="mt-6 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => reset()}
            className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-neutral-950"
          >
            Try again
          </button>
          <Link
            href="/"
            className="rounded border border-neutral-300 px-4 py-2 font-medium text-neutral-700 hover:border-blue-400 hover:text-blue-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:border-neutral-700 dark:text-neutral-300 dark:hover:text-blue-400 dark:focus-visible:ring-offset-neutral-950"
          >
            Back to catalog
          </Link>
        </div>
      </div>
    </main>
  );
}
