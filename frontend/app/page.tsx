"use client";

/**
 * Home route — the Service Catalog (Phase 7 Slice 1).
 *
 * The primary browse experience: paginated catalog, category filter chips, linked
 * cards. Each of the four query states (loading / error / empty / success) is handled
 * explicitly. This page is the reference for how the rest of the app talks to the API:
 * typed client → React Query hook → presentational components.
 *
 * NOTE on cold starts: the live API (Render free tier) sleeps when idle. The first
 * request after idle can take 30–90s to wake up — that's expected, not a bug, and the
 * skeleton loading state covers it.
 */
import { useState } from "react";
import { useServicesCatalog, useCatalogCategories } from "@/lib/api/queries";
import { ServiceList } from "@/components/service-list";
import { CategoryFilter } from "@/components/category-filter";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";

const PAGE_SIZE = 10;

export default function HomePage() {
  // The page owns browse state: which category is selected and which page we're on.
  // Keeping it here (not in the URL) is fine for Slice 1; a later slice can lift it
  // into search params for shareable links.
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [page, setPage] = useState(0);

  // Catalog query is filter-aware: pass categoryId to filter, null for the full list.
  const catalog = useServicesCatalog({ categoryId, page, size: PAGE_SIZE });

  // Category chips come from a separate query that derives distinct categories from a
  // large catalog page. No `GET /api/categories` call — see useCatalogCategories.
  const categoriesQuery = useCatalogCategories();

  /** Switch category: reset to page 0 (a filtered view's page numbers differ). */
  function handleSelectCategory(next: number | null) {
    setCategoryId(next);
    setPage(0);
  }

  const isLoading = catalog.isPending;
  const isError = catalog.isError;

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="mb-8">
        <h1 className="text-3xl font-bold tracking-tight">Service Marketplace</h1>
        <p className="mt-1 text-neutral-600 dark:text-neutral-400">
          Browse services from local providers.
        </p>
      </header>

      {/* Category filter — hidden while categories haven't loaded or are empty. */}
      <CategoryFilter
        categories={categoriesQuery.data ?? []}
        selectedCategoryId={categoryId}
        onSelect={handleSelectCategory}
        disabled={isLoading}
      />

      <div className="mt-6">
        {isLoading ? (
          <CatalogSkeleton />
        ) : isError ? (
          <ErrorState
            error={catalog.error}
            onRetry={() => catalog.refetch()}
            title="Couldn't load services."
          />
        ) : (
          <>
            {catalog.isFetching ? (
              <p className="mb-4 text-sm text-neutral-500">Refreshing…</p>
            ) : null}
            <ServiceList page={catalog.data} />
            <Pagination
              number={catalog.data?.number}
              totalPages={catalog.data?.totalPages}
              first={catalog.data?.first}
              last={catalog.data?.last}
              onPageChange={setPage}
              disabled={catalog.isFetching}
            />
          </>
        )}
      </div>
    </main>
  );
}
