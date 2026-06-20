/**
 * Typed booking client — thin wrappers over apiGet/apiPost/apiPut for the booking
 * endpoints. Mirrors the layout of `services.ts`: this file knows the booking domain
 * (path params, the Spring `Page<T>` shape), the generic client knows HTTP.
 *
 * All three endpoints require a Bearer token; `client.ts` auto-attaches the in-memory
 * access token (and does the 401 single-flight refresh), so these wrappers don't
 * touch tokens directly.
 *
 * SCOPE NOTE: payments are intentionally NOT here. `POST /api/payments` belongs to Slice 4
 * (payment); this slice is bookings only.
 */
import { apiGet, apiPost, apiPut } from "./client";
import type { components } from "./schema";

/** A single booking row, typed straight from the generated schema. */
export type Booking = components["schemas"]["BookingResponse"];

/** Spring `Page<Booking>` shape. Pinned to the generated schema (auto-updates on gen:api). */
export type BookingPage = components["schemas"]["PageBookingResponse"];

/** Body for creating a booking. serviceId/startTime/endTime required; quantity/notes optional. */
export type BookingCreateRequest = components["schemas"]["BookingCreateRequest"];

/** The booking lifecycle statuses (mirrors the backend state machine). */
export type BookingStatus = NonNullable<Booking["status"]>;

/** Pagination options for the "my bookings" list. */
export interface ListBookingsParams {
  page?: number; // 0-based, matches Spring's Pageable
  size?: number;
}

/**
 * List the authenticated customer's bookings (`GET /api/bookings`).
 *
 * The endpoint returns the caller's own bookings (server-side filtered by the JWT),
 * so there's no customerId param to pass — the token decides whose bookings come back.
 */
export async function listMyBookings(
  params: ListBookingsParams = {},
): Promise<BookingPage> {
  const { page = 0, size = 10 } = params;
  return apiGet("/api/bookings", { query: { page, size } }) as Promise<BookingPage>;
}

/** Params for creating a booking. */
export interface CreateBookingParams {
  serviceId: number;
  startTime: string; // ISO 8601 (date-time) — caller converts datetime-local → ISO
  endTime: string;
  quantity?: number;
  notes?: string;
}

/**
 * Create a booking (`POST /api/bookings`).
 *
 * Possible failures (all surface as ApiError with the matching .status):
 *   - 400 invalid input (e.g. endTime before startTime)
 *   - 404 service not found
 *   - 422 service not available for the requested times
 * The pages map these to user-facing messages.
 */
export async function createBooking(
  params: CreateBookingParams,
): Promise<Booking> {
  return apiPost("/api/bookings", params) as Promise<Booking>;
}

/** Params for cancelling a booking. */
export interface CancelBookingParams {
  id: number;
}

/**
 * Cancel a pending booking (`PUT /api/bookings/{id}/cancel`).
 *
 * NOTE the method is PUT, not POST — `client.apiPut` handles it. No request body.
 *
 * Possible failures:
 *   - 404 booking not found
 *   - 422 not yours, or status no longer allows cancel (e.g. already CONFIRMED/COMPLETED)
 * The UI gates the Cancel button to PENDING-only; a 422 here usually means the status
 * changed under us (vendor confirmed between our list render and the click) — show the
 * server message and refetch.
 */
export async function cancelBooking(params: CancelBookingParams): Promise<Booking> {
  const path = `/api/bookings/${encodeURIComponent(params.id)}/cancel`;
  // No body for this PUT — apiPut leaves Content-Type off when body is undefined.
  return apiPut(path) as Promise<Booking>;
}
