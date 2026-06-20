/**
 * Typed reviews client — thin wrappers over apiGet/apiPost for the review endpoints.
 *
 * Reviews are the LAST step of the booking lifecycle. The full lifecycle is
 * PENDING → CONFIRMED → IN_PROGRESS → COMPLETED; once a booking reaches COMPLETED, the
 * customer who owns it may leave exactly one review. The provider-side lifecycle
 * transitions (confirm/start/complete) live in `provider-bookings.ts`; this file owns the
 * review resource itself (create + the two public read endpoints).
 *
 * AUTH MODEL — mixed, unlike most resource files here:
 *   - POST /api/reviews                         (AUTHED — the booking's customer)
 *   - GET  /api/reviews/service/{serviceId}     (PUBLIC — powers the service detail page)
 *   - GET  /api/reviews/provider/{providerId}       (PUBLIC — reserved for a future provider
 *                                                 profile page; no UI wires it yet)
 * The two GETs are intentionally public so the service detail page can show reviews to
 * logged-out browsers. `client.ts` still attaches a token if present (harmless for a
 * public read), and runs the 401 single-flight refresh on the POST.
 *
 * Mirrors the layout of `bookings.ts` / `services.ts`: this file owns the review domain
 * (path params, the request/response shapes from the generated schema), the generic
 * `client.ts` owns HTTP.
 */
import { apiGet, apiPost } from "./client";
import type { components } from "./schema";

/** A single review row, typed straight from the generated schema. */
export type Review = components["schemas"]["ReviewResponse"];

/** Body for creating a review (bookingId + 1–5 rating required; comment optional). */
export type ReviewCreateRequest = components["schemas"]["ReviewCreateRequest"];

/** Params for creating a review. */
export interface CreateReviewParams {
  body: ReviewCreateRequest;
}

/**
 * Create a review (`POST /api/reviews`).
 *
 * Requires the caller to be the customer who owns the booking, and the booking to be
 * COMPLETED. Exactly one review per booking is allowed — a second attempt returns 422.
 *
 * The rating is an integer 1–5 (the form enforces this client-side via star buttons);
 * comment is optional, max 2000 chars. We validate client-side for instant feedback and
 * to avoid a wasted round-trip, but the server is the source of truth.
 *
 * Possible failures (all surface as ApiError with the matching .status):
 *   - 400 invalid input (rating out of range, comment too long, etc.)
 *   - 404 booking not found
 *   - 422 not your booking / booking not COMPLETED / already reviewed
 * The form maps 422 "already reviewed" to a settled "Reviewed" state (treat it as done,
 * since the outcome — a review exists — is what the customer wanted).
 */
export async function createReview(
  params: CreateReviewParams,
): Promise<Review> {
  return apiPost("/api/reviews", params.body) as Promise<Review>;
}

/** Params for listing a service's reviews. */
export interface ListServiceReviewsParams {
  serviceId: number;
}

/**
 * List the reviews for a service (`GET /api/reviews/service/{serviceId}`).
 *
 * PUBLIC endpoint (no auth required) — the service detail page renders this to anyone,
 * including logged-out visitors. Returns `ReviewResponse[]` newest-first (the ordering
 * is fixed by the backend; we render in the order received). There's no pagination here:
 * reviews per service are expected to be a small bounded set for this app.
 */
export async function listServiceReviews(
  params: ListServiceReviewsParams,
): Promise<Review[]> {
  const path = `/api/reviews/service/${encodeURIComponent(params.serviceId)}`;
  return apiGet(path) as Promise<Review[]>;
}

/** Params for listing a provider's reviews. */
export interface ListProviderReviewsParams {
  providerId: number;
}

/**
 * List the reviews for a provider (`GET /api/reviews/provider/{providerId}`).
 *
 * PUBLIC endpoint. Aggregates reviews across all of a provider's services. Reserved for a
 * future provider-profile page; no UI wires it yet, but the helper is here so that page
 * can drop in `useProviderReviews(providerId)` without touching this file.
 */
export async function listProviderReviews(
  params: ListProviderReviewsParams,
): Promise<Review[]> {
  const path = `/api/reviews/provider/${encodeURIComponent(params.providerId)}`;
  return apiGet(path) as Promise<Review[]>;
}
