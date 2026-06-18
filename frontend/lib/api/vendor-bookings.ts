/**
 * Typed vendor-bookings client — thin wrappers over apiGet/apiPut for the vendor side
 * of the booking workflow.
 *
 * Mirrors the layout of `bookings.ts` (the customer side), but is intentionally a
 * SEPARATE file: the vendor's view of a booking is a different use case (advancing
 * incoming requests through their lifecycle vs. the customer managing their own
 * requests), and the endpoints are different (`/api/bookings/vendor` vs `/api/bookings`;
 * `/{id}/confirm|start|complete` vs `/{id}/cancel`). Keeping them apart prevents
 * accidentally coupling the two flows and makes each file's intent obvious.
 *
 * We REUSE the `Booking` type from `bookings.ts` — the wire shape is identical
 * (`BookingResponse`), so there's no reason to redefine it. A vendor booking and a
 * customer booking render the same fields; only the actions differ.
 *
 * All endpoints require a VENDOR-role JWT (the vendor who owns the service the booking
 * is on). `client.ts` auto-attaches the in-memory access token and runs the 401
 * single-flight refresh. Role enforcement is server-side (403 if not the owning
 * vendor); the client also gates the route via <RequireAuth requireRole="VENDOR">.
 *
 * SCOPE (per the backend contract) — the vendor drives the booking lifecycle forward:
 *   - GET /api/bookings/vendor?page&size  -> Page<BookingResponse> (bookings on this
 *                                            vendor's services, ALL statuses)
 *   - PUT /api/bookings/{id}/confirm      -> BookingResponse (PENDING   -> CONFIRMED)
 *   - PUT /api/bookings/{id}/start        -> BookingResponse (CONFIRMED -> IN_PROGRESS)
 *   - PUT /api/bookings/{id}/complete     -> BookingResponse (IN_PROGRESS -> COMPLETED)
 *
 * The full lifecycle is PENDING -> CONFIRMED -> IN_PROGRESS -> COMPLETED (with CANCELLED
 * as a side exit). The vendor advances it one step at a time: confirm lets the customer
 * pay (an Order can only be created from a CONFIRMED booking), start marks work begun,
 * complete marks it done — which is also what unblocks the customer to leave a review.
 * All three transitions are bodyless PUTs sharing the same shape; only the path suffix
 * differs, so they're near-identical wrappers below.
 */
import { apiGet, apiPut } from "./client";
import type { Booking } from "./bookings";
import type { components } from "./schema";

/** Spring `Page<BookingResponse>` shape for the vendor's bookings. Same as customer side. */
export type VendorBookingPage = components["schemas"]["PageBookingResponse"];

/** Pagination options for the vendor's booking list. */
export interface ListVendorBookingsParams {
  page?: number; // 0-based, matches Spring's Pageable
  size?: number;
}

/**
 * List the bookings on the authenticated vendor's services
 * (`GET /api/bookings/vendor`).
 *
 * Returns ALL statuses (PENDING/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED) so the
 * vendor sees their full booking pipeline — pending requests to confirm, upcoming
 * confirmed work, and history. The endpoint is JWT-scoped server-side: the vendor id
 * comes from the token, and the backend joins bookings whose service.vendorId matches.
 * There's no vendorId param to pass.
 */
export async function listVendorBookings(
  params: ListVendorBookingsParams = {},
): Promise<VendorBookingPage> {
  const { page = 0, size = 10 } = params;
  return apiGet("/api/bookings/vendor", {
    query: { page, size },
  }) as Promise<VendorBookingPage>;
}

/** Params for a booking state transition (confirm/start/complete all take just an id). */
export interface ConfirmBookingParams {
  id: number;
}

/**
 * Internal helper: perform a bodyless PUT against a booking state-transition endpoint.
 *
 * confirm/start/complete share the exact same contract — a path-param id, no request
 * body, a `BookingResponse` return, and the same failure modes (403/404/422) — so we
 * centralize the path-building + bodyless-PUT here and expose three thin named wrappers.
 * `apiPut` leaves Content-Type off when the body is undefined, which these endpoints
 * require (a bodyless PUT).
 *
 * Failure modes (same for all three transitions):
 *   - 403 not the vendor who owns this booking's service (defense in depth — the UI
 *     only shows these buttons to vendors, and only the owning vendor may advance)
 *   - 404 booking not found
 *   - 422 not your service, or the booking's current status doesn't allow this
 *     transition (e.g. the customer cancelled between our list render and the click, or
 *     the row was already advanced) — the UI gates each button to its source status, so
 *     a 422 here usually means a race; we show the server message and the
 *     invalidation-on-success refetches the list with the true status.
 */
async function transitionBooking(id: number, action: string): Promise<Booking> {
  const path = `/api/bookings/${encodeURIComponent(id)}/${action}`;
  return apiPut(path) as Promise<Booking>;
}

/**
 * Confirm a pending booking (`PUT /api/bookings/{id}/confirm`).
 *
 * Transitions status PENDING → CONFIRMED. This is the gate for the customer's pay
 * flow: an Order can only be created from a CONFIRMED booking, so confirming is what
 * makes "book → confirm → pay" fully clickable end-to-end in the browser.
 */
export async function confirmBooking(
  params: ConfirmBookingParams,
): Promise<Booking> {
  return transitionBooking(params.id, "confirm");
}

/** Params for starting a booking (same shape as confirm — just the id). */
export interface StartBookingParams {
  id: number;
}

/**
 * Start a confirmed booking (`PUT /api/bookings/{id}/start`).
 *
 * Transitions status CONFIRMED → IN_PROGRESS, marking the agreed work as begun. The
 * UI gates this button to CONFIRMED-only. See `transitionBooking` for failure modes.
 */
export async function startBooking(params: StartBookingParams): Promise<Booking> {
  return transitionBooking(params.id, "start");
}

/** Params for completing a booking (same shape as confirm — just the id). */
export interface CompleteBookingParams {
  id: number;
}

/**
 * Complete an in-progress booking (`PUT /api/bookings/{id}/complete`).
 *
 * Transitions status IN_PROGRESS → COMPLETED. This is the final vendor-side step, and
 * it's also what unblocks the CUSTOMER to leave a review (a review requires a COMPLETED
 * booking). The UI gates this button to IN_PROGRESS-only. See `transitionBooking` for
 * failure modes.
 */
export async function completeBooking(
  params: CompleteBookingParams,
): Promise<Booking> {
  return transitionBooking(params.id, "complete");
}
