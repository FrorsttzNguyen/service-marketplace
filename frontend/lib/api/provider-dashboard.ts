import { apiGet } from "./client";
import type { components } from "./schema";

/** Provider dashboard statistics, typed from the generated OpenAPI schema. */
export type ProviderStats = components["schemas"]["ProviderStatsResponse"];

/** Provider dashboard earnings, typed from the generated OpenAPI schema. */
export type ProviderEarnings = components["schemas"]["ProviderEarningsResponse"];

export async function getProviderStats(): Promise<ProviderStats> {
  return apiGet("/api/provider/dashboard/stats") as Promise<ProviderStats>;
}

export async function getProviderEarnings(): Promise<ProviderEarnings> {
  return apiGet("/api/provider/dashboard/earnings") as Promise<ProviderEarnings>;
}
