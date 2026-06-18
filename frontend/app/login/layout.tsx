import type { Metadata } from "next";

/**
 * Segment metadata for `/login`.
 *
 * The page itself is a client island (`"use client"`) because it uses React
 * state + hooks, so it can't export a `metadata` object (Next only honors
 * metadata from server components). A sibling server `layout.tsx` is the
 * idiomatic place to declare static segment metadata in that case: it merges
 * with the root layout's `title.template`, producing "Log in · Service
 * Marketplace" as the document title. The layout body just renders children.
 */
export const metadata: Metadata = {
  title: "Log in",
};

export default function LoginLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
