import { apiGet } from "./client";
import type { components } from "./schema";

/** Vendor dashboard statistics, typed from the generated OpenAPI schema. */
export type VendorStats = components["schemas"]["VendorStatsResponse"];

/** Vendor dashboard earnings, typed from the generated OpenAPI schema. */
export type VendorEarnings = components["schemas"]["VendorEarningsResponse"];

export async function getVendorStats(): Promise<VendorStats> {
  return apiGet("/api/vendor/dashboard/stats") as Promise<VendorStats>;
}

export async function getVendorEarnings(): Promise<VendorEarnings> {
  return apiGet("/api/vendor/dashboard/earnings") as Promise<VendorEarnings>;
}
