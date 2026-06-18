import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";
import { Header } from "@/components/header";

/**
 * Root metadata.
 *
 * The App Router merges metadata declared here with any per-route `metadata`
 * exports. Child routes can set a plain `title` and it will be slotted into the
 * `title.template` below — e.g. /login setting `title: "Log in"` produces
 * "Log in · Service Marketplace". Routes that don't set a title fall back to the
 * `title.default`, so every page has a sensible document title for tabs,
 * history, bookmarks, and screen readers.
 *
 * `metadataBase` resolves any RELATIVE metadata URLs (here we have none yet, but
 * OpenGraph tags that we add later — or that Next infers — need a base to be
 * absolute, which is what social scrapers and link unfurlers require). We read
 * it from NEXT_PUBLIC_SITE_URL when available and otherwise let Next fall back to
 * the request origin, which keeps local dev correct without a hard-coded URL.
 *
 * `openGraph` provides the basic unfurl card: title/description/type. We
 * intentionally do NOT point at an external image — the app ships no asset, and
 * a broken OG image is worse than none. The owner can add one post-deploy.
 */
const siteUrl =
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Service Marketplace",
    template: "%s · Service Marketplace",
  },
  description:
    "Browse and book services from local providers — from home cleaning to photography and more.",
  openGraph: {
    title: "Service Marketplace",
    description:
      "Browse and book services from local providers — from home cleaning to photography and more.",
    type: "website",
  },
};

/**
 * Root layout (server component). Wraps every route in the React Query + Auth
 * providers (a client island) and renders the shared header. Any page/component
 * can `useQuery` / `useAuth` against the API.
 */
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <Header />
          {children}
        </Providers>
      </body>
    </html>
  );
}
