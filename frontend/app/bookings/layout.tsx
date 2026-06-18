import type { Metadata } from "next";

/**
 * Segment metadata for `/bookings`. The page is a client island (React Query +
 * state), so static metadata is declared here in a server `layout.tsx` and
 * merged with the root template → "My bookings · Service Marketplace".
 */
export const metadata: Metadata = {
  title: "My bookings",
};

export default function BookingsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
