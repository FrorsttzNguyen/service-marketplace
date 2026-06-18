"use client";

/**
 * Reusable error state for catalog/detail fetches.
 *
 * Why a shared component: the loading/empty/error trio appears on every data view.
 * Centralizing the error card (with its CORS hint, which is the most common local-dev
 * failure) keeps the message consistent and the page files short. The optional
 * `notFound` variant is for 404s, which read differently from a network/CORS failure.
 *
 * Visual (Phase 7): a danger-tinted island (Card) with a soft shadow. The retry button
 * uses the destructive variant so it reads as a recovery action against the red error.
 */
import { ApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface ErrorStateProps {
  error: unknown;
  onRetry?: () => void;
  /** Distinguish "doesn't exist" (404) from "couldn't reach the API". */
  notFound?: boolean;
  /** Title text; defaults fit most call sites. */
  title?: string;
  /** Hint shown under the message. Pass null to suppress (e.g. detail page 404). */
  hint?: React.ReactNode;
}

/**
 * Decide whether to show the local-dev CORS hint. The CORS block surfaces as a
 * `TypeError: Failed to fetch` (browser blocks before any status arrives), so we show
 * the hint for any non-ApiError error OR an ApiError with a network-ish status (0).
 * Real HTTP errors (4xx/5xx) reached the server, so CORS is fine — no hint needed.
 */
function shouldShowCorsHint(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.status === 0; // 0 = network failure that never got a response
  }
  return true; // plain Error → almost always a CORS/failed-to-fetch in the browser
}

export function ErrorState({
  error,
  onRetry,
  notFound = false,
  title,
  hint,
}: ErrorStateProps) {
  const message =
    error instanceof Error ? error.message : "Unknown error.";

  const showCorsHint = hint !== null && (hint ?? shouldShowCorsHint(error));

  return (
    <Card
      padded
      className="border-danger/30 bg-danger/10 text-danger"
      data-testid={notFound ? "not-found-state" : "error-state"}
      role="alert"
    >
      <p className="font-semibold">
        {title ?? (notFound ? "Not found." : "Something went wrong.")}
      </p>
      {!notFound ? <p className="mt-1 text-sm">{message}</p> : null}
      {showCorsHint ? (
        <p className="mt-2 text-xs opacity-90">
          {hint ?? (
            <>
              If you&apos;re running locally, this is often a CORS block: the live
              Render API must allow <code>http://localhost:3000</code> via
              <code> APP_CORS_ALLOWED_ORIGINS</code>.
            </>
          )}
        </p>
      ) : null}
      {onRetry ? (
        <Button
          variant="destructive"
          size="sm"
          className="mt-4"
          onClick={onRetry}
        >
          Try again
        </Button>
      ) : null}
    </Card>
  );
}
