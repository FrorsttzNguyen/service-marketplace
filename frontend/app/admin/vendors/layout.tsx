import type { Metadata } from "next";

/**
 * Segment metadata for `/admin/vendors`. The page is a client island (React Query +
 * state), so static metadata is declared here in a server `layout.tsx` and merged with
 * the root template → "Provider approvals · HandyHub".
 *
 * `robots: { index: false }` keeps an admin-only route out of search indexes even if it
 * were ever accidentally crawlable — defense in depth on top of the auth gate.
 */
export const metadata: Metadata = {
  title: "Provider approvals",
  robots: { index: false, follow: false },
};

export default function AdminVendorsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
