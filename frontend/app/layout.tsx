import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";
import { Header } from "@/components/header";

export const metadata: Metadata = {
  title: "Service Marketplace",
  description: "Browse services from local providers.",
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
