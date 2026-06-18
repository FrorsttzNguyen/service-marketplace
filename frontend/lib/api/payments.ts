/**
 * Typed payment client — thin wrappers over apiPost/apiGet for the payment endpoints.
 *
 * Mirrors the layout of `orders.ts` / `bookings.ts`: this file knows the payment
 * domain (path params + the PaymentCreateRequest body, the clientSecret-bearing
 * PaymentResponse), the generic `client.ts` knows HTTP.
 *
 * Both endpoints require a Bearer token; `client.ts` auto-attaches the in-memory
 * access token (and does the 401 single-flight refresh).
 *
 * ROLE IN THE CHECKOUT FLOW: a Payment is the Stripe PaymentIntent record backing an
 * Order. `createPayment` is the ONLY call that returns a `clientSecret` — that secret
 * is what Stripe.js <Elements> needs to mount the card UI. The GET endpoints return
 * the same PaymentResponse shape but with `clientSecret: null` (you can't reuse a
 * secret to remount Elements after a reload — see the checkout page for the v1
 * limitation this implies).
 */
import { apiGet, apiPost } from "./client";
import type { components } from "./schema";

/** A single payment, typed straight from the generated schema (no hand-rolled DTO). */
export type Payment = components["schemas"]["PaymentResponse"];

/** Body for creating a payment — orderId + paymentMethod. Mirrors PaymentCreateRequest. */
export type PaymentCreateRequest = components["schemas"]["PaymentCreateRequest"];

/** The payment lifecycle statuses (mirrors the backend / Stripe PaymentIntent status). */
export type PaymentStatus = NonNullable<Payment["status"]>;

/**
 * The payment methods we support in the UI. The backend treats paymentMethod as a
 * free-form string, but v1 of the checkout flow only ever sends "card" (Stripe's
 * PaymentElement is card-focused). Kept as a literal type so the call site can't
 * typo it; widening to a union later (e.g. "ideal" | "bancontact") is a one-liner.
 */
export type SupportedPaymentMethod = "card";

/** Params for creating a payment for an order. */
export interface CreatePaymentParams {
  orderId: number;
  /** v1 supports "card" only. See SupportedPaymentMethod above. */
  paymentMethod?: SupportedPaymentMethod;
}

/**
 * Create a payment (Stripe PaymentIntent) for an order
 * (`POST /api/payments { orderId, paymentMethod }`).
 *
 * This is the call that returns the `clientSecret` — Stripe.js needs it to mount
 * <Elements> + <PaymentElement> and to confirm the card client-side. Keep the secret
 * only as long as needed (it's not a long-lived credential, but still: don't log it,
 * don't persist it beyond the checkout session).
 *
 * Possible failures (all surface as ApiError with the matching .status):
 *   - 404 order not found
 *   - 409 a payment already exists for this order (see KNOWN LIMITATION below)
 *   - 422 order is not eligible for payment (e.g. already PAID)
 *
 * KNOWN v1 LIMITATION — 409 "payment already exists":
 *   The GET /api/payments/order/{orderId} endpoint returns the existing payment but
 *   with clientSecret = null (secrets are minted once, on POST). That means after a
 *   checkout-page reload we CANNOT remount Stripe <Elements> for the same order — we
 *   have no secret to feed it. The checkout page handles 409 by showing "a payment
 *   already exists for this order" + the current backend status from GET, rather than
 *   attempting a resume flow. A future slice would add a "resume payment" endpoint
 *   that re-mints a secret for a PENDING/PROCESSING payment; out of scope for v1.
 */
export async function createPayment(
  params: CreatePaymentParams,
): Promise<Payment> {
  const body: PaymentCreateRequest = {
    orderId: params.orderId,
    // Default to "card" — the only method the v1 PaymentElement supports.
    paymentMethod: params.paymentMethod ?? "card",
  };
  return apiPost("/api/payments", body) as Promise<Payment>;
}

/** Params for fetching the payment backing an order. */
export interface GetPaymentForOrderParams {
  orderId: number;
}

/**
 * Fetch the payment for an order (`GET /api/payments/order/{orderId}`).
 *
 * NOTE: this returns PaymentResponse with `clientSecret: null`. Only POST mints a
 * secret. So this call is for *status polling* (after a client-side confirm, to see
 * whether the Stripe webhook has flipped the backend status to SUCCEEDED), NOT for
 * (re)mounting Stripe Elements. See `createPayment` for the v1 resume limitation.
 *
 * Possible failures:
 *   - 403 not authorized (not the order owner)
 *   - 404 no payment exists yet for this order (e.g. before createPayment runs)
 */
export async function getPaymentForOrder(
  params: GetPaymentForOrderParams,
): Promise<Payment> {
  const path = `/api/payments/order/${encodeURIComponent(params.orderId)}`;
  return apiGet(path) as Promise<Payment>;
}
