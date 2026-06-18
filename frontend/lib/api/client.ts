/**
 * Thin fetch wrapper around the Service Marketplace backend.
 *
 * Why this exists: TanStack Query handles caching/loading/error *state*, but it still
 * needs a function that actually performs the HTTP call. Keeping that logic in one
 * place means every endpoint uses the same base URL, headers, error handling, and
 * 401-refresh-and-retry — no fetch boilerplate duplicated across hooks/pages.
 *
 * DEPENDENCY LAYERING (to avoid a circular import with auth):
 *   shared.ts (leaf) ← client.ts ← auth-context.tsx
 *                        ↑
 *                        └── token-store.ts ← auth-api.ts
 * `client.ts` reads the access token from `token-store` synchronously and triggers a
 * single-flight refresh on 401. It does NOT import React or the auth context.
 */
import {
  ApiError,
  BASE_URL,
  buildQueryString,
  toApiError,
  type QueryParams,
} from "./shared";
import {
  clearSession,
  getAccessToken,
  refreshAccessToken,
} from "@/lib/auth/token-store";

// Re-exported so existing imports (`import { ApiError } from "@/lib/api/client"`)
// keep working after the extraction into shared.ts.
export { ApiError, BASE_URL, buildQueryString };
export type { QueryParams };

/** Shared options for GET/POST (query only applies to GET). */
interface RequestOptions {
  query?: QueryParams;
  /**
   * Optional Bearer token. If omitted, the in-memory access token from the token
   * store is used automatically (so authed requests self-attach). Pass `null` to
   * force an anonymous request (e.g. for public endpoints you want to keep clean).
   */
  token?: string | null;
  signal?: AbortSignal;
  /**
   * When true (default), a 401 triggers a single-flight refresh + one retry. Set to
   * false to opt out (not currently used, but reserved for endpoints that should
   * surface 401 directly without retrying).
   */
  retryOn401?: boolean;
}

/**
 * Core request executor. Centralizes URL building, token attachment, and the 401
 * refresh-and-retry so GET/POST/PUT/DELETE share the exact same behavior. A JSON body
 * is sent only for methods that carry one (POST always; PUT only when provided; GET
 * and DELETE never carry a body).
 */
async function request(
  method: "GET" | "POST" | "PUT" | "DELETE",
  path: string,
  body: unknown,
  options: RequestOptions,
): Promise<unknown> {
  const doFetch = (token: string | null): Promise<Response> => {
    const headers: Record<string, string> = { Accept: "application/json" };
    // POST always sends a body; PUT sends one only when the caller provided it
    // (e.g. PUT /api/bookings/{id}/cancel has no body — we must not set Content-Type
    // on a bodyless request, or some servers/proxies reject it).
    const hasBody = method === "POST" || (method === "PUT" && body !== undefined);
    if (hasBody) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = `Bearer ${token}`;

    return fetch(`${BASE_URL}${path}${buildQueryString(options?.query)}`, {
      method,
      headers,
      body: hasBody ? JSON.stringify(body) : undefined,
      signal: options?.signal,
      cache: "no-store",
    });
  };

  // Resolve the token: explicit override > in-memory access token.
  const initialToken =
    options?.token !== undefined ? options.token : getAccessToken();

  let response = await doFetch(initialToken ?? null);

  // 401 self-heal: try one refresh, then replay the original request once.
  if (
    response.status === 401 &&
    options?.retryOn401 !== false &&
    initialToken === getAccessToken() // only retry if no other refresh already rotated it
  ) {
    const newToken = await refreshAccessToken().catch((err: unknown) => {
      // Refresh failed (expired/invalid refresh token). Tear down the session and
      // let the original 401 propagate — don't infinite-loop on refresh's own 401.
      clearSession();
      throw err;
    });

    if (newToken) {
      response = await doFetch(newToken);
    }
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  // DELETE (and any other method that may return 204 No Content) has no body to parse.
  // `response.json()` would throw on an empty body, so for DELETE we resolve void. The
  // caller (vendor-services deactivator) doesn't need a payload — a 2xx IS the success.
  if (method === "DELETE") return undefined;

  return response.json();
}

/**
 * Perform a GET against the backend.
 *
 * @param path API path with a leading slash, e.g. "/api/services" (static) or
 *            "/api/services/5" (dynamic — the `{id}` segment already substituted in).
 *            `services.ts` substitutes path params, keeping this function format-agnostic.
 * @returns Parsed JSON as `unknown`, narrowed at the call site via schema aliases.
 */
export async function apiGet(
  path: string,
  options?: Omit<RequestOptions, "body">,
): Promise<unknown> {
  return request("GET", path, undefined, options ?? {});
}

/**
 * Perform a JSON POST against the backend. Used by authed app endpoints (bookings,
 * orders, …). NOTE: the auth endpoints themselves (login/register/refresh) live in
 * `auth-api.ts` and use raw fetch deliberately — see that file's header.
 *
 * @param path API path with a leading slash, e.g. "/api/bookings".
 * @param body Request body (JSON-serializable). Shape is the caller's responsibility;
 *             type it from the generated schema at the call site.
 */
export async function apiPost(
  path: string,
  body: unknown,
  options?: Omit<RequestOptions, "query" | "body">,
): Promise<unknown> {
  return request("POST", path, body, options ?? {});
}

/**
 * Perform a JSON PUT against the backend. Used by state-change endpoints that are
 * idempotent-ish mutations, e.g. `PUT /api/bookings/{id}/cancel`.
 *
 * `body` is OPTIONAL — some PUT endpoints (cancel) take no request body. When omitted
 * (undefined), no body and no Content-Type header are sent.
 *
 * Shares the exact same token-attach + 401-refresh-and-retry path as apiGet/apiPost.
 *
 * @param path API path with a leading slash, e.g. "/api/bookings/12/cancel".
 * @param body Request body, or undefined for a bodyless PUT.
 */
export async function apiPut(
  path: string,
  body?: unknown,
  options?: Omit<RequestOptions, "query" | "body">,
): Promise<unknown> {
  return request("PUT", path, body, options ?? {});
}

/**
 * Perform a DELETE against the backend. Used by soft-delete/state-change endpoints
 * that the backend models as DELETE, e.g. `DELETE /api/vendor/services/{id}` (which
 * deactivates → INACTIVE; the row is NOT hard-deleted despite the HTTP verb).
 *
 * DELETE never carries a request body here. The request executor resolves `void`
 * on success because DELETE endpoints in this API return 204 No Content — there's
 * no JSON to parse.
 *
 * Shares the exact same token-attach + 401-refresh-and-retry path as apiGet/apiPost/apiPut.
 *
 * @param path API path with a leading slash, e.g. "/api/vendor/services/42".
 * @returns Resolves `undefined` on success (204 No Content has no body).
 */
export async function apiDelete(
  path: string,
  options?: Omit<RequestOptions, "query" | "body">,
): Promise<unknown> {
  return request("DELETE", path, undefined, options ?? {});
}
