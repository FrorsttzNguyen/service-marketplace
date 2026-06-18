import type { Metadata } from "next";

/**
 * Segment metadata for `/register`. See app/login/layout.tsx for why metadata
 * lives in a server `layout.tsx` next to a client page. Merges with the root
 * `title.template` → "Create an account · Service Marketplace".
 */
export const metadata: Metadata = {
  title: "Create an account",
};

export default function RegisterLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
