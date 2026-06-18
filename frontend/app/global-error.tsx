"use client";

/**
 * Global error boundary (`global-error.tsx`).
 *
 * This is the boundary of LAST RESORT: it catches errors that escape the root
 * `layout.tsx` itself (e.g. an error thrown during the layout render). Because
 * the root layout is the thing that's broken, Next does NOT wrap this file in
 * it — so this file must render its OWN `<html>` and `<body>` and pull in any
 * styles it needs itself. It's a fully standalone client document.
 *
 * Keep it tiny and dependency-free: no app imports, no providers, no context.
 * If something deeper is broken (a bad import, a provider crash), those
 * dependencies could be the thing that's failing — so we avoid them entirely.
 * Only React + Next's `<html>`/`<body>` intrinsic elements and inline styling.
 * Tailwind IS available because its base + utilities are injected into the
 * document head by Next, but inline styles are the safest fallback here because
 * they don't rely on the Tailwind pipeline having built successfully.
 *
 * Like `error.tsx`, `reset` re-renders the boundary — a second-chance recovery
 * without leaking any error details.
 *
 * Visual (Phase 7): the inline styles echo the new palette (soft indigo tint
 * background, white island, rounded corners, indigo accent) so the last-resort
 * page still feels on-brand. Kept as inline styles per the file's standalone
 * constraint.
 */
interface GlobalErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalError({ error, reset }: GlobalErrorProps) {
  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "1rem",
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
          color: "#1e1b2e",
          // Soft indigo tint wash — matches the light-mode page background token.
          background:
            "radial-gradient(1200px 600px at 50% -10%, #e0e7ff 0%, #f4f5fc 60%)",
        }}
      >
        <div
          style={{
            maxWidth: "28rem",
            textAlign: "center",
            background: "#ffffff",
            padding: "2rem 1.75rem",
            borderRadius: "1.25rem",
            boxShadow: "0 8px 32px rgba(16,24,40,0.10)",
          }}
        >
          <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>
            Something went wrong
          </h1>
          <p style={{ marginTop: "0.5rem", color: "#6b6f82" }}>
            The app hit an unexpected error. Try again, or reload the page.
          </p>
          {error?.digest ? (
            <p
              style={{
                marginTop: "0.75rem",
                fontSize: "0.75rem",
                color: "#9aa0b5",
              }}
            >
              Reference: {error.digest}
            </p>
          ) : null}
          <button
            type="button"
            onClick={() => reset()}
            style={{
              marginTop: "1.5rem",
              padding: "0.625rem 1.25rem",
              borderRadius: "9999px",
              border: "none",
              background: "#6366f1",
              color: "#ffffff",
              fontWeight: 600,
              cursor: "pointer",
              boxShadow: "0 4px 12px rgba(99,102,241,0.30)",
            }}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
