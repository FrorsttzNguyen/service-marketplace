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

/** All shapes pinned to the generated schema; a spec change flows through here. */
export type AuthResponse = components["schemas"]["AuthResponse"];
export type TokenRefreshResponse = components["schemas"]["TokenRefreshResponse"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];
export type LoginRequest = components["schemas"]["LoginRequest"];
/**
 * Full user profile returned by `GET /api/auth/me`. Richer than AuthResponse (adds id,
 * phoneNumber, status, timestamps) — used by rehydrate to rebuild the in-memory user.
 */
export type UserResponse = components["schemas"]["UserResponse"];

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
 * Shared GET helper for auth endpoints that DO need the access token (currently /me).
 *
 * Why raw fetch + an explicit token (not `apiGet` from client.ts):
 *   1. Same anti-recursion reason as `authPost` — the generic client's
 *      401-refresh-and-retry would recurse if /me itself 401'd.
 *   2. Passing the token in explicitly (rather than importing `getAccessToken` from
 *      token-store) keeps this file a dependency LEAF. token-store already imports from
 *      here; an import the other way would close a circular dependency. The caller
 *      (token-store.rehydrate) reads its own in-memory token and hands it in.
 */
async function authGet<TResp>(
  path: string,
  accessToken: string,
  signal?: AbortSignal,
): Promise<TResp> {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
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

/**
 * Fetch the current user's profile (`GET /api/auth/me`).
 *
 * Why this exists: the refresh endpoint returns NO profile fields — only an access
 * token. So after a page reload, even a successful refresh leaves the in-memory `user`
 * null, which breaks the header name, RequireAuth.requireRole, and any role-gated UI.
 * /me fixes that: after rehydrate's refresh succeeds, we call /me with the freshly
 * minted access token and rebuild the user profile (including `role`).
 *
 * The caller MUST pass a non-empty access token; we don't fall back to anything here
 * (no implicit token store access — see `authGet` header for the leaf-import reason).
 *
 * Failure handling: a 401 here means the access token is bad/expired even after a
 * successful refresh (clock skew, server hiccup). The caller (rehydrate) catches it
 * and tears the session down — better a logged-out user than a half-built profile.
 */
export function me(
  accessToken: string,
  signal?: AbortSignal,
): Promise<UserResponse> {
  return authGet<UserResponse>("/api/auth/me", accessToken, signal);
}
