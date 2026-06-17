/**
 * Leaf HTTP primitives shared by `client.ts` (authed REST) and `auth-api.ts`
 * (the login/register/refresh endpoints).
 *
 * Why this file exists: the 401-refresh-and-retry in `client.ts` needs to call the
 * refresh endpoint, and the auth context needs `apiPost`-style calls. To avoid a
 * circular import (client ↔ auth), the low-level pieces — base URL, query-string
 * builder, the ApiError class — live here, a dependency-free leaf that both sides
 * can import without referencing each other or React.
 */

/**
 * Resolve the API base URL once. Trailing slashes are stripped so we can safely
 * join paths with a leading slash. If the env var is missing we fall back to
 * localhost — useful when running the Spring backend locally on :8080.
 */
export const BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");

/**
 * Custom error thrown for any non-2xx response. TanStack Query surfaces this as
 * `query.error`, so pages can branch on it without try/catching the fetch itself.
 * Carrying the status + raw text lets a caller distinguish 401 (auth) from 500 etc.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly bodyText: string;

  constructor(status: number, bodyText: string, message?: string) {
    super(message ?? `API request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.bodyText = bodyText;
  }
}

/** Query string params where each value is string|number|undefined (skipped if undefined). */
export type QueryParams = Record<string, string | number | undefined | null>;

/** Build a query string, dropping undefined/null entries (Spring rejects empty `?&`). */
export function buildQueryString(params?: QueryParams): string {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    search.append(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

/**
 * Parse a non-2xx response into an ApiError. Reads the body as text first because it
 * may not be JSON (e.g. an HTML error page). Tries to extract a human-readable message
 * from a Spring error payload (`{ "message": "..." }` / `{ "error": "..." }`) so forms
 * can show something useful instead of just "400".
 */
export async function toApiError(response: Response): Promise<ApiError> {
  const bodyText = await response.text().catch(() => "");
  return new ApiError(response.status, bodyText, extractMessage(bodyText, response.status));
}

/**
 * Best-effort extraction of a user-facing message from an error response body.
 * Falls back to a status-based generic. Never throws.
 */
function extractMessage(bodyText: string, status: number): string | undefined {
  if (bodyText) {
    try {
      const parsed = JSON.parse(bodyText);
      // Spring's default error shape, plus our app's usual fields.
      const msg =
        parsed?.message ?? parsed?.error ?? parsed?.errors?.[0]?.defaultMessage;
      if (typeof msg === "string" && msg.length > 0) return msg;
    } catch {
      // Not JSON — fall through to generic.
    }
  }
  switch (status) {
    case 400:
      return "The request was invalid. Please check your input.";
    case 401:
      return "Invalid email or password.";
    case 403:
      return "You don't have permission to do that.";
    case 404:
      return "Not found.";
    case 409:
      return "That email is already registered.";
    default:
      return undefined;
  }
}
