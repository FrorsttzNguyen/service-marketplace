import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/providers";
import { Header } from "@/components/header";

/*
 * Friendly rounded body font via next/font (Inter).
 *
 * next/font self-hosts the font (no Google Fonts request at runtime → no layout
 * shift, no privacy hit). `variable` exposes the loaded font family as the
 * `--font-inter` CSS variable on <html>, which globals.css + tailwind.config.ts
 * both reference as the sans default. `display: "swap"` lets text render in the
 * fallback immediately and swap once Inter arrives.
 */
const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

/**
 * Root metadata.
 *
 * The App Router merges metadata declared here with any per-route `metadata`
 * exports. Child routes can set a plain `title` and it will be slotted into the
 * `title.template` below — e.g. /login setting `title: "Log in"` produces
 * "Log in · HandyHub". Routes that don't set a title fall back to the
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
    default: "HandyHub",
    template: "%s · HandyHub",
  },
  description:
    "Book trusted home-service pros near you — cleaning, repairs, and more. Pick a time, pay securely.",
  openGraph: {
    title: "HandyHub",
    description:
      "Book trusted home-service pros near you — cleaning, repairs, and more. Pick a time, pay securely.",
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
    <html lang="en" className={inter.variable}>
      <body>
        <Providers>
          <Header />
          {children}
        </Providers>
      </body>
    </html>
  );
}
