import type { Metadata } from "next";

/**
 * Segment metadata for `/services/[id]`.
 *
 * The detail page is a client island hitting the live API, so we deliberately
 * do NOT add a `generateMetadata` that refetches the service server-side — that
 * would double the requests, fight the cold-start latency of the live API, and
 * require the backend URL to be reachable from the Next server (which it isn't
 * always, e.g. behind the dev proxy). A static title for this segment is the
 * right trade-off: it gives tabs/history/bookmarks a consistent label while the
 * specific service title renders inside the page body.
 *
 * The title merges with the root template → "Service details · Service
 * Marketplace". The page is client, so metadata lives in this sibling server
 * layout (Next only honors metadata from server components).
 */
export const metadata: Metadata = {
  title: "Service details",
};

export default function ServiceDetailLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
