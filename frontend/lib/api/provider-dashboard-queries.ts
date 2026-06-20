"use client";

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import {
  getProviderEarnings,
  getProviderStats,
  type ProviderEarnings,
  type ProviderStats,
} from "./provider-dashboard";

export const providerDashboardKeys = {
  all: ["provider", "dashboard"] as const,
  stats: () => [...providerDashboardKeys.all, "stats"] as const,
  earnings: () => [...providerDashboardKeys.all, "earnings"] as const,
} as const;

export function useProviderStats(): UseQueryResult<ProviderStats> {
  return useQuery({
    queryKey: providerDashboardKeys.stats(),
    queryFn: () => getProviderStats(),
    staleTime: 30_000,
  });
}

export function useProviderEarnings(): UseQueryResult<ProviderEarnings> {
  return useQuery({
    queryKey: providerDashboardKeys.earnings(),
    queryFn: () => getProviderEarnings(),
    staleTime: 30_000,
  });
}
