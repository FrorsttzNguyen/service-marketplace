/**
 * TanStack Query hook for the public service catalog.
 *
 * Why a hook (not calling `listServices` directly in the component):
 * - Centralizes the query key (`["services", page, size]`) so every consumer shares
 *   the same cache entry; changing the page invalidates/refetches correctly.
 * - Keeps loading/error/refetch wiring out of the JSX.
 *
 * `staleTime: 30s` — the catalog doesn't change every second, and the live API
 * sleeps on the free tier, so re-fetching on every mount would be slow + wasteful.
 */
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { listServices, type ServicePage } from "./services";

export interface UseServicesOptions {
  page?: number;
  size?: number;
}

export function useServices(
  options: UseServicesOptions = {},
): UseQueryResult<ServicePage> {
  const { page = 0, size = 10 } = options;
  return useQuery({
    queryKey: ["services", page, size],
    queryFn: ({ signal }) => listServices({ page, size }), // signal wiring kept simple for Slice 0
    staleTime: 30_000,
  });
}
