"use client";

/**
 * TanStack Query hooks + mutations for provider service management (Phase 7 Slice 6).
 *
 * Mirrors the `admin-queries.ts` / `bookings-queries.ts` pattern: a centralized
 * query-key factory so invalidation and keying stay in sync, one list query + four
 * mutations (create/update/activate/deactivate). Every mutation invalidates the whole
 * `["provider", "services", ...]` family on success so the list refetches with the new
 * state — e.g. an Activate moves a DRAFT into the live catalog, and a Deactivate drops
 * it out, both reflected on the next render without each call site doing it manually.
 *
 * The four mutations cover the provider's full service lifecycle from the UI:
 *   create (DRAFT) → activate (ACTIVE) ↔ deactivate (INACTIVE), with update at any time.
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import {
  activateProviderService,
  createProviderService,
  deactivateProviderService,
  listProviderServices,
  updateProviderService,
  type ServiceCreateRequest,
  type ServiceUpdateRequest,
  type ProviderService,
  type ProviderServicePage,
} from "./provider-services";

/**
 * Centralized provider-service query keys. The `list` key includes the page (and size),
 * so paging creates a fresh cache entry (and the previous one stays cached for
 * back-navigation). `all` is used for broad invalidation on mutations — any create /
 * activate / deactivate / update changes the list's contents across every page.
 */
export const providerServiceKeys = {
  all: ["provider", "services"] as const,
  lists: () => [...providerServiceKeys.all, "list"] as const,
  list: (page: number, size: number) =>
    [...providerServiceKeys.lists(), { page, size }] as const,
} as const;

/** Pagination options for the provider's service list. */
export interface UseProviderServicesOptions {
  page?: number;
  size?: number;
}

/**
 * Fetch the authenticated provider's own services (paginated, ALL statuses). Returns
 * the standard query result so callers can branch on isPending/isError/data. The
 * endpoint requires a VENDOR-role JWT; a 403 surfaces as an error (the RequireAuth
 * gate means we never reach here as a non-provider in normal navigation, but defense
 * in depth still matters).
 */
export function useProviderServices(
  options: UseProviderServicesOptions = {},
): UseQueryResult<ProviderServicePage> {
  const { page = 0, size = 10 } = options;
  return useQuery({
    queryKey: providerServiceKeys.list(page, size),
    // `signal` is destructured for parity with the other query hooks (bookings/services/
    // admin); the typed wrapper doesn't currently thread it through, matching the
    // established pattern across all resource hooks.
    queryFn: ({ signal }) => listProviderServices({ page, size }),
    staleTime: 30_000,
  });
}

/**
 * Create a service. On success, invalidates the provider-services family so the list
 * refetches and the new DRAFT row appears. The page (not the hook) handles form
 * validation + error UX (e.g. a 400 message from the backend).
 */
export function useCreateProviderService(): UseMutationResult<
  ProviderService,
  unknown,
  ServiceCreateRequest
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ServiceCreateRequest) => createProviderService({ body }),
    onSuccess: () => {
      // Broad invalidation: a new service changes every page of the list.
      void queryClient.invalidateQueries({ queryKey: providerServiceKeys.all });
    },
  });
}

/**
 * Update a service. Takes `{ id, body }` so the hook stays in charge of both the path
 * param and the (partial) update body. On success, invalidates the family so the list
 * reflects the edited fields. The page handles per-row spinner + 404/403 messaging.
 */
export function useUpdateProviderService(): UseMutationResult<
  ProviderService,
  unknown,
  { id: number; body: ServiceUpdateRequest }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }) => updateProviderService({ id, body }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: providerServiceKeys.all });
    },
  });
}

/**
 * Activate a service (DRAFT|INACTIVE → ACTIVE). On success, invalidates the family so
 * the list reflects the new status and the service enters the public catalog.
 */
export function useActivateProviderService(): UseMutationResult<
  ProviderService,
  unknown,
  number // service id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => activateProviderService({ id }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: providerServiceKeys.all });
    },
  });
}

/**
 * Deactivate a service (ACTIVE → INACTIVE, soft delete). Resolves `void` (the backend
 * returns 204). On success, invalidates the family so the list reflects the new status
 * and the service leaves the public catalog. The page still treats the mutation as
 * "succeeded" via the standard onSuccess path — the absence of a payload is fine.
 */
export function useDeactivateProviderService(): UseMutationResult<
  void,
  unknown,
  number // service id
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deactivateProviderService({ id }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: providerServiceKeys.all });
    },
  });
}
