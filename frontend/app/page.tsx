"use client";

/**
 * Home route — the Phase 7 Slice 0 smoke page.
 *
 * Fetches `GET /api/services` (paginated) through the typed client + TanStack Query
 * and renders the total count + a list of service names. Each of the four query
 * states (loading / error / empty / success) is handled explicitly so this page is
 * the reference for how the rest of the app should talk to the API.
 *
 * NOTE on cold starts: the live API (Render free tier) sleeps when idle. The first
 * request after idle can take 30–90s to wake up — that's expected, not a bug, and
 * the loading state below covers it.
 */
import { useServices } from "@/lib/api/queries";
import { ServiceList } from "@/components/service-list";

export default function HomePage() {
  const { data, isPending, isError, error, refetch, isFetching } = useServices({
    page: 0,
    size: 10,
  });

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight">Service Marketplace</h1>
        <p className="mt-1 text-neutral-600 dark:text-neutral-400">
          Browse services from local providers.
        </p>
      </header>

      {isPending ? (
        <p
          className="rounded border border-neutral-200 p-6 text-neutral-500 dark:border-neutral-800 dark:text-neutral-400"
          data-testid="loading-state"
        >
          Loading services… (the live API may take up to 90s to wake up on a cold
          start.)
        </p>
      ) : isError ? (
        <div
          className="rounded border border-red-300 bg-red-50 p-6 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
          data-testid="error-state"
        >
          <p className="font-semibold">Couldn&apos;t load services.</p>
          <p className="mt-1 text-sm">
            {error instanceof Error ? error.message : "Unknown error."}
          </p>
          <p className="mt-2 text-xs text-red-600 dark:text-red-400">
            If you&apos;re running locally, this is often a CORS block: the live
            Render API must allow <code>http://localhost:3000</code> via
            <code> APP_CORS_ALLOWED_ORIGINS</code>.
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-4 rounded bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
          >
            Try again
          </button>
        </div>
      ) : (
        <>
          {isFetching ? (
            <p className="mb-4 text-sm text-neutral-500">Refreshing…</p>
          ) : null}
          <ServiceList page={data} />
        </>
      )}
    </main>
  );
}
