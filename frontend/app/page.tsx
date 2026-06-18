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
 *
 * Visual (Phase 7): a soft hero island (gradient tint + headline + subcopy + the
 * category chips) sits above the catalog grid; both float as separate rounded islands
 * on the tinted page wash.
 */
import { useState } from "react";
import { useServicesCatalog, useCatalogCategories } from "@/lib/api/queries";
import { ServiceList } from "@/components/service-list";
import { CategoryFilter } from "@/components/category-filter";
import { Pagination } from "@/components/pagination";
import { ErrorState } from "@/components/error-state";
import { CatalogSkeleton } from "@/components/skeletons";
import { Container } from "@/components/ui/container";

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
    <Container width="default">
      {/*
        Hero island — a soft indigo→violet gradient over the bright card surface,
        with the page headline + subcopy + the category chips nested inside. The
        gradient is subtle (low opacity stops) so it stays readable and
        portfolio-appropriate, not neon. It's its own island so it reads as the
        page's welcome banner, visually distinct from the catalog grid below.
      */}
      <section
        aria-label="Welcome"
        className="mb-8 overflow-hidden rounded-2xl border border-border/60 bg-gradient-to-br from-accent-soft via-card to-accent-soft p-6 shadow-island sm:p-10"
      >
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">
          Local pros, one click away
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Service Marketplace
        </h1>
        <p className="mt-2 max-w-xl text-muted-foreground">
          Browse services from local providers — from home cleaning to
          photography and more.
        </p>

        {/* Category filter — nested in the hero so the chips share its island.
            Hidden while categories haven't loaded or are empty. */}
        <div className="mt-6">
          <CategoryFilter
            categories={categoriesQuery.data ?? []}
            selectedCategoryId={categoryId}
            onSelect={handleSelectCategory}
            disabled={isLoading}
          />
        </div>
      </section>

      <div>
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
              <p className="mb-4 text-sm text-muted-foreground">Refreshing…</p>
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
    </Container>
  );
}
