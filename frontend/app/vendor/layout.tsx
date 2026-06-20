import type { Metadata } from "next";

/**
 * Segment metadata for `/vendor/*`. The pages under this route are client islands
 * (React Query + form state), so static metadata is declared here in a server
 * `layout.tsx` and merged with the root template.
 *
 * `robots: { index: false, follow: false }` keeps vendor-only routes out of search
 * indexes even if they were ever accidentally crawlable — defense in depth on top of
 * the <RequireAuth requireRole="VENDOR"> gate. This mirrors the admin route's layout.
 *
 * Note: segment layouts cascade, so a per-page `layout.tsx` under `/vendor/services`
 * or `/vendor/bookings` is not required for the noindex goal — this covers both. The
 * individual pages can still set a more specific `title` via their own `metadata`
 * export if desired (currently they rely on the default title template).
 */
export const metadata: Metadata = {
  title: "Provider",
  robots: { index: false, follow: false },
};

export default function VendorLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
