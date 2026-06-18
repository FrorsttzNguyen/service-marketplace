"use client";

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import {
  getVendorEarnings,
  getVendorStats,
  type VendorEarnings,
  type VendorStats,
} from "./vendor-dashboard";

export const vendorDashboardKeys = {
  all: ["vendor", "dashboard"] as const,
  stats: () => [...vendorDashboardKeys.all, "stats"] as const,
  earnings: () => [...vendorDashboardKeys.all, "earnings"] as const,
} as const;

export function useVendorStats(): UseQueryResult<VendorStats> {
  return useQuery({
    queryKey: vendorDashboardKeys.stats(),
    queryFn: () => getVendorStats(),
    staleTime: 30_000,
  });
}

export function useVendorEarnings(): UseQueryResult<VendorEarnings> {
  return useQuery({
    queryKey: vendorDashboardKeys.earnings(),
    queryFn: () => getVendorEarnings(),
    staleTime: 30_000,
  });
}
