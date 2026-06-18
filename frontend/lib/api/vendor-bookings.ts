/**
 * Typed vendor-bookings client — thin wrappers over apiGet/apiPut for the vendor side
 * of the booking workflow.
 *
 * Mirrors the layout of `bookings.ts` (the customer side), but is intentionally a
 * SEPARATE file: the vendor's view of a booking is a different use case (confirming
 * incoming requests vs. the customer managing their own requests), and the endpoints
 * are different (`/api/bookings/vendor` vs `/api/bookings`; `/{id}/confirm` vs
 * `/{id}/cancel`). Keeping them apart prevents accidentally coupling the two flows
 * and makes each file's intent obvious.
 *
 * We REUSE the `Booking` type from `bookings.ts` — the wire shape is identical
 * (`BookingResponse`), so there's no reason to redefine it. A vendor booking and a
 * customer booking render the same fields; only the actions differ (Confirm vs Cancel).
 *
 * All endpoints require a VENDOR-role JWT (the vendor who owns the service the booking
 * is on). `client.ts` auto-attaches the in-memory access token and runs the 401
 * single-flight refresh. Role enforcement is server-side (403 if not the owning
 * vendor); the client also gates the route via <RequireAuth requireRole="VENDOR">.
 *
 * SCOPE (per the backend contract):
 *   - GET /api/bookings/vendor?page&size  -> Page<BookingResponse> (bookings on this
 *                                            vendor's services, ALL statuses)
 *   - PUT /api/bookings/{id}/confirm      -> BookingResponse (PENDING -> CONFIRMED)
 *
 * IMPORTANT: the backend does NOT expose start/complete endpoints to the vendor UI
 * (those transitions happen via other flows). Only `confirm` is wired here — do NOT
 * call any start/complete endpoint. Confirming a booking is what unblocks the customer
 * to pay (an Order can only be created from a CONFIRMED booking).
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

/** Params for confirming a booking. */
export interface ConfirmBookingParams {
  id: number;
}

/**
 * Confirm a pending booking (`PUT /api/bookings/{id}/confirm`).
 *
 * Transitions status PENDING → CONFIRMED. This is the gate for the customer's pay
 * flow: an Order can only be created from a CONFIRMED booking, so confirming is what
 * makes "book → confirm → pay" fully clickable end-to-end in the browser. No request
 * body — the only meaningful input is the path param. NOTE the method is PUT, not POST
 * — `client.apiPut` handles it with no Content-Type (bodyless PUT).
 *
 * Possible failures:
 *   - 403 not the vendor who owns this booking's service (defense in depth — the UI
 *     only shows this button to vendors, and only the owning vendor may confirm)
 *   - 404 booking not found
 *   - 422 status no longer allows confirm (e.g. customer cancelled between our list
 *     render and the click, or it was already confirmed) — the UI gates the button to
 *     PENDING-only; a 422 here usually means a race, so we show the server message and
 *     the invalidation-on-success refetches the list with the true status.
 */
export async function confirmBooking(
  params: ConfirmBookingParams,
): Promise<Booking> {
  const path = `/api/bookings/${encodeURIComponent(params.id)}/confirm`;
  // No body for this PUT — apiPut leaves Content-Type off when body is undefined.
  return apiPut(path) as Promise<Booking>;
}
