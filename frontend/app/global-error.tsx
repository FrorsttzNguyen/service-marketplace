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
 * Only React + Next's `<html>`/`<body>` intrinsic elements and inline-ish
 * styling. Tailwind IS available because its base + utilities are injected into
 * the document head by Next, but we keep classes minimal and resilient.
 *
 * Like `error.tsx`, `reset` re-renders the boundary — a second-chance recovery
 * without leaking any error details.
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
          color: "#171717",
          background: "#ffffff",
        }}
      >
        <div style={{ maxWidth: "28rem", textAlign: "center" }}>
          <h1 style={{ fontSize: "1.5rem", fontWeight: 700, margin: 0 }}>
            Something went wrong
          </h1>
          <p style={{ marginTop: "0.5rem", color: "#525252" }}>
            The app hit an unexpected error. Try again, or reload the page.
          </p>
          {error?.digest ? (
            <p style={{ marginTop: "0.75rem", fontSize: "0.75rem", color: "#a3a3a3" }}>
              Reference: {error.digest}
            </p>
          ) : null}
          <button
            type="button"
            onClick={() => reset()}
            style={{
              marginTop: "1.5rem",
              padding: "0.5rem 1rem",
              borderRadius: "0.375rem",
              border: "1px solid #d4d4d4",
              background: "#ffffff",
              color: "#171717",
              fontWeight: 500,
              cursor: "pointer",
            }}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
