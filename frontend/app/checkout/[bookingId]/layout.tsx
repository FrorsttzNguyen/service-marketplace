import type { Metadata } from "next";

/**
 * Segment metadata for `/checkout/[bookingId]`. Static title only — the page is
 * a client island orchestrating order/payment creation, and we don't want to
 * refetch server-side. Merges with the root template → "Checkout · Service
 * Marketplace".
 */
export const metadata: Metadata = {
  title: "Checkout",
};

export default function CheckoutLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
