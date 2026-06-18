"use client";

/**
 * Stripe.js loader — single shared Stripe instance for the checkout flow.
 *
 * Why a singleton (memoized) loader: `loadStripe(pk)` is async and idempotent, but
 * calling it repeatedly would re-run the network fetch of Stripe.js on every render.
 * The official Stripe guidance is to call `loadStripe` ONCE outside any component
 * (or memoize it). We do it lazily here so the stripe.js script is only downloaded
 * when a checkout route actually mounts — not on every page load across the app.
 *
 * SECURITY MODEL:
 *   - The publishable key (`pk_test_...` in test mode) is NOT a secret — it's safe to
 *     ship to the browser (that's what the NEXT_PUBLIC_ prefix means). It can only be
 *     used to *create* tokens/PaymentIntents client-side, never to charge or read
 *     sensitive data. The secret key (`sk_test_...`) lives ONLY on the backend.
 *   - We never hardcode a key value. It must come from the environment. The checkout
 *     page guards against a missing key and shows a clear config message instead of
 *     crashing — see `getStripePublishableKey` + the page's `MissingStripeConfig`
 *     branch.
 */
import { loadStripe, type Stripe } from "@stripe/stripe-js";

/**
 * Read the Stripe publishable key from the client environment.
 *
 * Returns the trimmed key, or null if unset/blank. Kept as a separate function so the
 * checkout page can branch cleanly ("no key → show config message") and so a test
 * could stub `process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` without touching the
 * loader.
 *
 * NOTE: because this var is `NEXT_PUBLIC_`, Next.js inlines its value at build time.
 * A missing var bakes in `undefined`, which we normalize to null here.
 */
export function getStripePublishableKey(): string | null {
  const raw = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY;
  if (!raw) return null;
  const trimmed = raw.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/**
 * Memoized loadStripe promise.
 *
 * Initialized lazily by `getStripe()` (not at module load) so we never fetch the
 * Stripe.js script until a checkout route actually needs it. Once set, every caller
 * shares the same promise — no duplicate script downloads.
 */
let stripePromise: Promise<Stripe | null> | null = null;

/**
 * Resolve the shared Stripe instance, or null if no publishable key is configured.
 *
 * Callers should `getStripePublishableKey()` FIRST to decide whether to render the
 * checkout UI at all; this function is the fallback loader that still returns a
 * (resolving-to-null) promise if called without a key, so a stray call can't throw.
 *
 * Why resolve null instead of rejecting on a missing key: rejecting would force every
 * caller into try/catch for a config problem that's already surfaced by the page-level
 * guard. A null result is simpler to branch on.
 */
export function getStripe(): Promise<Stripe | null> {
  if (stripePromise) return stripePromise; // share the in-flight / resolved promise
  const key = getStripePublishableKey();
  if (!key) {
    // No key → resolve null. The checkout page guards before reaching here, but this
    // keeps a stray call from throwing.
    stripePromise = Promise.resolve(null);
    return stripePromise;
  }
  stripePromise = loadStripe(key);
  return stripePromise;
}
