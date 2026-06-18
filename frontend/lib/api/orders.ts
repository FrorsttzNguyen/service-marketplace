/**
 * Typed order client — thin wrappers over apiPost/apiGet for the order endpoints.
 *
 * Mirrors the layout of `services.ts` / `bookings.ts`: this file knows the order
 * domain (path params + the CreateOrderRequest body shape), the generic `client.ts`
 * knows HTTP. Callers (the checkout page) depend on these intent-revealing names
 * instead of raw path strings, so a backend path rename only touches this file.
 *
 * Both endpoints require a Bearer token; `client.ts` auto-attaches the in-memory
 * access token (and does the 401 single-flight refresh), so these wrappers never
 * touch tokens directly.
 *
 * ROLE IN THE CHECKOUT FLOW: an Order is the prerequisite for a Payment. We create
 * it from a CONFIRMED booking (idempotent — see createOrder below), then hand its id
 * to the payment step. The order also carries the final amount + currency shown on
 * the checkout page.
 */
import { apiGet, apiPost } from "./client";
import type { components } from "./schema";

/** A single order, typed straight from the generated schema (no hand-rolled DTO). */
export type Order = components["schemas"]["OrderResponse"];

/** Body for creating an order — just the bookingId. Mirrors CreateOrderRequest. */
export type OrderCreateRequest = components["schemas"]["CreateOrderRequest"];

/** The order lifecycle statuses (mirrors the backend state machine). */
export type OrderStatus = NonNullable<Order["status"]>;

/**
 * Create an order from a confirmed booking (`POST /api/orders { bookingId }`).
 *
 * IDEMPOTENCY: the backend is idempotent on bookingId — if a *payable* order already
 * exists for this booking, it returns that order (still 201) instead of duplicating.
 * This is what makes the checkout page safe to reload: re-POSTing the same bookingId
 * just hands us back the same order, and we proceed to the payment step.
 *
 * Possible failures (all surface as ApiError with the matching .status):
 *   - 404 booking not found
 *   - 422 booking is not CONFIRMED, the existing order is no longer payable
 *         (e.g. already PAID), or the caller is not the booking's customer
 * The checkout page maps these to user-facing messages.
 */
export async function createOrder(
  params: OrderCreateRequest,
): Promise<Order> {
  return apiPost("/api/orders", params) as Promise<Order>;
}

/** Params for fetching a single order by its id. */
export interface GetOrderParams {
  id: number;
}

/**
 * Fetch one order's details (`GET /api/orders/{id}`).
 *
 * Used by the checkout page to re-read the order after a reload (the id comes from
 * the create-order step in memory; on reload we re-create it idempotently instead,
 * so this GET is mostly reserved for debugging/future use). The `{id}` segment from
 * the OpenAPI template is substituted here so `apiGet` only ever sees a concrete path.
 *
 * Possible failures:
 *   - 404 order not found
 *   - 422 caller is not the order's customer
 */
export async function getOrder(params: GetOrderParams): Promise<Order> {
  const path = `/api/orders/${encodeURIComponent(params.id)}`;
  return apiGet(path) as Promise<Order>;
}
