"use client";

/**
 * React Query provider wrapper.
 *
 * Why this is a separate client component: Next.js App Router server components can't
 * hold React context providers (providers run on every render and need a stable
 * client instance). The root `layout.tsx` is a server component, so it renders this
 * client island, which owns the `QueryClient` for the whole app.
 *
 * `useState` (not a module-level const) guarantees each browser tab / SSR request
 * gets its own `QueryClient`. Sharing one across requests would leak cache between
 * users — a classic Next.js data-isolation bug.
 */
import { useState, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // Don't auto-refetch on window focus in dev — the live API sleeps and
            // each refetch can take 30–90s on a cold start. Keep the UX calm.
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
