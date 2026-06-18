import Link from "next/link";

/**
 * App Router `not-found.tsx` — the 404 page.
 *
 * Next renders this component whenever a route doesn't match (or a page throws
 * the special `notFound()`). It's a SERVER component, so it can't use hooks —
 * just static, friendly markup. We deliberately mirror the rest of the app's
 * visual language (max-w container, neutral palette, blue links) so a 404 reads
 * as part of the product, not a generic Next.js error page.
 *
 * Why no image / illustration: the project intentionally ships no external
 * assets, and a styled-text 404 stays light, accessible, and on-brand.
 */
export default function NotFound() {
  return (
    <main className="mx-auto max-w-2xl px-4 py-16 text-center">
      <p className="text-sm font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
        404
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">
        We can&apos;t find that page
      </h1>
      <p className="mx-auto mt-3 max-w-md text-neutral-600 dark:text-neutral-400">
        The link may be broken, or the page may have been moved or removed. Browse
        the catalog to get back on track.
      </p>
      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <Link
          href="/"
          className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-neutral-950"
        >
          Browse services
        </Link>
        <Link
          href="/bookings"
          className="rounded border border-neutral-300 px-4 py-2 font-medium text-neutral-700 hover:border-blue-400 hover:text-blue-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:border-neutral-700 dark:text-neutral-300 dark:hover:text-blue-400 dark:focus-visible:ring-offset-neutral-950"
        >
          My bookings
        </Link>
      </div>
    </main>
  );
}
