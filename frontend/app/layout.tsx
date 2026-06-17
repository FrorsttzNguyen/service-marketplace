import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "Service Marketplace",
  description: "Browse services from local providers.",
};

/**
 * Root layout (server component). Wraps every route in the React Query provider
 * (a client island) so any page/component can `useQuery` against the API.
 */
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
