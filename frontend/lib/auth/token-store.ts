/**
 * Token store — the single source of truth for auth state, framework-agnostic.
 *
 * SECURITY MODEL (hard rules from the project owner):
 *   - ACCESS token: IN-MEMORY ONLY (the module-level `accessToken` variable below).
 *     Survives client-side navigation but NOT a full reload. Never written to any
 *     persistent storage. This limits the damage if a refresh-token-stealing XSS runs:
 *     the attacker gets at most the current short-lived access token, not a durable
 *     credential.
 *   - REFRESH token: localStorage under REFRESH_TOKEN_KEY. Long-lived; needed to
 *     rehydrate a session across reloads. Cleared on logout.
 *   - No token value is ever logged.
 *
 * Why a module (not React state): the 401-retry in `client.ts` needs synchronous
 * read access to the access token on every request, outside the React render cycle.
 * React then mirrors this module via `auth-context.tsx` + a subscribe() API so the
 * UI re-renders when auth state changes.
 *
 * SSR safety: every localStorage access is guarded by `typeof window === "undefined"`
 * because Next.js renders parts of the tree on the server, where localStorage doesn't
 * exist. The in-memory `accessToken` is also per-JS-context, which on the server is
 * per-request-ish — fine, since we never put an access token there anyway.
 */
import { refresh as refreshEndpoint, me as meEndpoint } from "./auth-api";
import type { AuthResponse, UserResponse } from "./auth-api";

/** localStorage key for the refresh token. Single key, easy to clear. */
const REFRESH_TOKEN_KEY = "sm.refreshToken";

/**
 * The non-secret half of the session — what the UI shows in the header. Derived from
 * the AuthResponse on login/register/refresh. NOT stored in localStorage; it's rebuilt
 * from the refresh call on rehydrate.
 */
export interface AuthUser {
  userId: number;
  fullName: string;
  email: string;
  role: string;
}

// --- Module state (private) -------------------------------------------------

let accessToken: string | null = null; // IN-MEMORY ONLY — never persisted.
let user: AuthUser | null = null;

/** Listeners notified on any auth-state change (login/logout/refresh/rehydrate). */
const listeners = new Set<() => void>();

function emit() {
  for (const l of listeners) l();
}

/** Subscribe to auth-state changes; returns an unsubscribe function. */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

// --- Sync accessors (used by client.ts on every request) --------------------

/** Current in-memory access token, or null if logged out / not yet hydrated. */
export function getAccessToken(): string | null {
  return accessToken;
}

/** Current user (non-secret profile fields), or null. */
export function getUser(): AuthUser | null {
  return user;
}

export function isAuthenticated(): boolean {
  return accessToken !== null;
}

// --- localStorage (refresh token only) --------------------------------------

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

/** Read the stored refresh token. Returns "" (not null) when none stored. */
export function getRefreshToken(): string {
  if (!isBrowser()) return "";
  try {
    return window.localStorage.getItem(REFRESH_TOKEN_KEY) ?? "";
  } catch {
    // localStorage can throw (private mode / disabled) — treat as no token.
    return "";
  }
}

/** Persist the refresh token. No-op outside the browser. */
function setRefreshToken(token: string): void {
  if (!isBrowser()) return;
  try {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
  } catch {
    // If storage is unavailable the session just won't survive reload — acceptable.
  }
}

/** Remove the stored refresh token. No-op outside the browser. */
function clearRefreshToken(): void {
  if (!isBrowser()) return;
  try {
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    // ignore
  }
}

// --- Session apply / clear --------------------------------------------------

/** Derive the AuthUser subset from a full AuthResponse. */
function toUser(res: AuthResponse): AuthUser | null {
  if (res.userId === undefined || !res.email) return null;
  return {
    userId: res.userId,
    fullName: res.fullName ?? "",
    email: res.email,
    role: res.role ?? "CUSTOMER",
  };
}

/**
 * Derive the AuthUser subset from a /me response. /me returns the richer UserResponse
 * (uses `id`, not `userId`), so this mirrors toUser but maps the different field name.
 * Used by rehydrate() to rebuild the in-memory user after a reload.
 */
function toUserFromMe(res: UserResponse): AuthUser | null {
  if (res.id === undefined || !res.email) return null;
  return {
    userId: res.id,
    fullName: res.fullName ?? "",
    email: res.email,
    role: res.role ?? "CUSTOMER",
  };
}

/**
 * Apply a full auth response (login/register). Stores the access token in memory
 * and the refresh token in localStorage, then sets the user and notifies listeners.
 */
export function applySession(res: AuthResponse): void {
  if (res.accessToken) accessToken = res.accessToken;
  // refresh token is optional in the schema but present on login/register; keep it
  // only when provided. (The refresh endpoint does NOT return a new one.)
  if (res.refreshToken) setRefreshToken(res.refreshToken);
  user = toUser(res);
  emit();
}

/**
 * Clear the entire session: drop the in-memory access token, remove the refresh
 * token from localStorage, and reset the user. Used on logout and on a failed
 * rehydrate/refresh.
 */
export function clearSession(): void {
  accessToken = null;
  clearRefreshToken();
  user = null;
  emit();
}

// --- Single-flight refresh --------------------------------------------------

/**
 * In-flight refresh promise. When set, concurrent callers await the SAME promise
 * instead of firing multiple `/api/auth/refresh` calls (single-flight). This
 * prevents a refresh stampede when N requests all 401 at once.
 */
let inflightRefresh: Promise<string> | null = null;

/**
 * Attempt to refresh the access token exactly once (shared across concurrent 401s).
 *
 * Contract:
 *   - Resolves with the new access token on success (also stores it in memory +
 *     updates the user from the response — refresh doesn't return a new refreshToken,
 *     so the stored one is kept).
 *   - Rejects (ApiError) on failure; the caller (client.ts) catches it, clears the
 *     session, and lets the original 401 propagate.
 *   - While `inflightRefresh` is set, additional callers get the same promise — no
 *     second network call.
 *
 * Returns null if there is no refresh token to use (caller should not retry).
 */
export function refreshAccessToken(): Promise<string | null> {
  const stored = getRefreshToken();
  if (!stored) {
    // Nothing to refresh with — caller treats as a hard 401.
    return Promise.resolve(null);
  }

  if (inflightRefresh) return inflightRefresh; // share the in-flight call

  inflightRefresh = (async () => {
    try {
      const res = await refreshEndpoint(stored);
      if (!res.accessToken) {
        throw new Error("Refresh response missing accessToken");
      }
      // Refresh does NOT rotate the refresh token — keep the stored one.
      accessToken = res.accessToken;
      emit();
      return res.accessToken;
    } finally {
      // Clear the latch last so errors don't pin a rejected promise forever.
      inflightRefresh = null;
    }
  })();

  return inflightRefresh;
}

/**
 * Rehydrate the session on app boot (called from AuthProvider on mount).
 *
 * Two-step flow:
 *   1. If a refresh token exists, mint a fresh access token via /refresh.
 *   2. With that access token, call /me to rebuild the in-memory user profile.
 *
 * Why step 2: /refresh returns ONLY an access token (no profile fields). Without /me
 * the in-memory `user` would stay null after every reload — so the header wouldn't show
 * the user's name, RequireAuth.requireRole couldn't match a role, and admin/role-gated
 * UI wouldn't render even for a logged-in admin. /me fixes that one gap. We only need
 * it here: login/register already return the full profile (via applySession), so there's
 * no reason to call /me after those.
 *
 * Failure handling:
 *   - No refresh token → return immediately (logged-out state).
 *   - /refresh fails (expired/invalid → 401) → clearSession(), end logged-out.
 *   - /me fails after a successful /refresh → clearSession() too. A /me 401 right after
 *     a successful refresh means something's off (clock skew, server hiccup, revoked
 *     between the two calls). Tearing down avoids a logged-in-but-anonymous state where
 *     the header is blank but API calls would succeed — confusing and a bad UX. The user
 *     can simply re-login to get a clean session.
 */
export async function rehydrate(): Promise<void> {
  if (!getRefreshToken()) {
    return; // nothing to restore
  }
  try {
    const newToken = await refreshAccessToken();
    if (!newToken) {
      // refreshAccessToken resolved null = no refresh token available after all; nothing
      // to populate. Treat as logged-out.
      return;
    }
    // Fresh access token in hand — fetch the profile so role/name survive the reload.
    const profile = await meEndpoint(newToken);
    user = toUserFromMe(profile);
    emit();
  } catch {
    // Any failure (refresh 401, /me 401, network) → tear down and end logged-out.
    clearSession();
  }
}
