/**
 * Thin fetch wrapper around the Service Marketplace backend.
 *
 * Why this exists: TanStack Query handles caching/loading/error *state*, but it still
 * needs a function that actually performs the HTTP call. Keeping that logic in one
 * place means every endpoint uses the same base URL, headers, and error handling —
 * no fetch boilerplate duplicated across hooks/pages.
 *
 * The base URL comes from NEXT_PUBLIC_API_BASE_URL (Next.js inlines `NEXT_PUBLIC_*`
 * env vars into the client bundle at build time, so it's available in the browser).
 */

/**
 * Resolve the API base URL once. Trailing slashes are stripped so we can safely
 * join paths with a leading slash. If the env var is missing we fall back to
 * localhost — useful when running the Spring backend locally on :8080.
 */
const BASE_URL = (
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
function buildQueryString(params?: QueryParams): string {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    search.append(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

/** Options accepted by `apiGet`. Only GET is needed for the catalog so far. */
interface GetOptions {
  /** Query parameters; undefined/null values are omitted. */
  query?: QueryParams;
  /**
   * Optional Bearer token. Slice 0 has no auth yet, but threading it through the
   * client now means Slice 2 (auth) only has to pass a token in, not rewrite fetch.
   */
  token?: string;
  /**
   * AbortSignal — TanStack Query passes one so in-flight requests are cancelled
   * when a query is unmounted or its key changes (saves bandwidth + avoids races).
   */
  signal?: AbortSignal;
}

/**
 * Perform a GET against the backend.
 *
 * @param path API path with a leading slash, e.g. "/api/services" (static) or
 *            "/api/services/5" (dynamic — the `{id}` segment already substituted in).
 *            `services.ts` substitutes path params, keeping this function format-agnostic.
 * @returns Parsed JSON. Returned as `unknown` and narrowed at the call site (e.g.
 *          `as Promise<ServicePage>`); the generated schema is the source of truth for
 *          the shape, not the runtime value here.
 *
 * Why a plain `string` path (not `keyof paths`): the generated `paths` keys are the
 * *templates* (`"/api/services/{id}"`), so a concrete path like `"/api/services/5"`
 * wouldn't satisfy `keyof paths`. Widening to `string` unblocks dynamic routes without
 * weakening real safety — the cast + the schema-derived aliases in `services.ts` are
 * where the actual type guarantee lives.
 */
export async function apiGet(
  path: string,
  options?: GetOptions,
): Promise<unknown> {
  const url = `${BASE_URL}${path}${buildQueryString(options?.query)}`;

  const headers: Record<string, string> = {
    Accept: "application/json",
  };
  if (options?.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  const response = await fetch(url, {
    method: "GET",
    headers,
    signal: options?.signal,
    // Don't cache: the catalog is live data and TanStack Query owns caching policy.
    cache: "no-store",
  });

  if (!response.ok) {
    // Read the body as text first — it may not be JSON (e.g. an HTML error page).
    const bodyText = await response.text().catch(() => "");
    throw new ApiError(response.status, bodyText);
  }

  return response.json();
}
