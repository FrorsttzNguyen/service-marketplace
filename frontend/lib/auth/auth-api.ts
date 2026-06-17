/**
 * Auth endpoint wrappers: register, login, refresh.
 *
 * IMPORTANT — why these use raw `fetch` instead of `apiPost`/`apiGet`:
 * The generic client in `client.ts` auto-attaches the access token and, on a 401,
 * triggers a single-flight refresh + retry. That logic would recurse infinitely if
 * the *refresh call itself* 401'd (refresh failing → client tries to refresh → …).
 * To break that cycle, auth endpoints are implemented here with plain `fetch`:
 * they bypass the token + retry layer entirely. `apiPost` is reserved for authed
 * app requests (bookings, orders, … — Slice 3+).
 *
 * Types come straight from the generated schema — no hand-rolled DTOs.
 */
import { BASE_URL, ApiError, toApiError } from "@/lib/api/shared";
import type { components } from "@/lib/api/schema";

/** All four shapes pinned to the generated schema; a spec change flows through here. */
export type AuthResponse = components["schemas"]["AuthResponse"];
export type TokenRefreshResponse = components["schemas"]["TokenRefreshResponse"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];
export type LoginRequest = components["schemas"]["LoginRequest"];

/** Shared JSON POST helper for auth endpoints (no token, no retry — see file header). */
async function authPost<TBody, TResp>(
  path: string,
  body: TBody,
  signal?: AbortSignal,
): Promise<TResp> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
    signal,
    cache: "no-store",
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await response.json()) as TResp;
}

/**
 * Register a new account (`POST /api/auth/register`).
 * Success → 201 AuthResponse. 409 = email taken, 400 = invalid input (message parsed).
 */
export function register(
  body: RegisterRequest,
  signal?: AbortSignal,
): Promise<AuthResponse> {
  return authPost<RegisterRequest, AuthResponse>("/api/auth/register", body, signal);
}

/**
 * Log in (`POST /api/auth/login`). Success → 200 AuthResponse. 401 = bad creds.
 */
export function login(
  body: LoginRequest,
  signal?: AbortSignal,
): Promise<AuthResponse> {
  return authPost<LoginRequest, AuthResponse>("/api/auth/login", body, signal);
}

/**
 * Rotate the access token (`POST /api/auth/refresh`).
 *
 * NOTE: the refresh endpoint returns ONLY `{ accessToken, accessTokenExpiresAt }`.
 * The refresh token is long-lived and does NOT rotate — the caller keeps the stored
 * one and only the access token is replaced.
 */
export function refresh(
  refreshToken: string,
  signal?: AbortSignal,
): Promise<TokenRefreshResponse> {
  return authPost(
    "/api/auth/refresh",
    { refreshToken } satisfies components["schemas"]["RefreshTokenRequest"],
    signal,
  );
}
