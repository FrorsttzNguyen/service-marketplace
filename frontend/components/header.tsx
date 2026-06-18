"use client";

/**
 * Site header / nav. Shows the brand link plus auth-aware controls:
 *   - Logged out → "Log in" + "Sign up" links.
 *   - Logged in  → the user's name + a "Log out" button.
 *   - Admin user → an extra "Admin" link to /admin/vendors.
 *   - Vendor user → three extra links: "Dashboard" + "My services" + "Bookings"
 *     (vendor side) to /vendor/dashboard, /vendor/services, and /vendor/bookings.
 *   - Initializing → nothing (avoid a flash of logged-out controls during rehydrate).
 *
 * The access token is intentionally never read here — only the non-secret user
 * profile (name, role) from useAuth(), which mirrors the token-store.
 *
 * Role-gating the Admin/Vendor links depends on `user.role` surviving a page reload,
 * which in turn depends on /me being called during rehydrate (see token-store.rehydrate).
 * Without that wiring, an admin/vendor reloading any page would briefly appear role-less
 * and the links would flicker off; rehydrate now populates the role before this renders.
 *
 * Visual (Phase 7 polish): the bar is a floating rounded "island" — a bright card
 * that sits on the tinted page wash with a soft shadow and generous spacing. It is
 * sticky so it stays in view while scrolling. Brand wordmark uses the primary
 * accent on hover; auth controls use Button (ghost + primary) so they match the
 * rest of the app.
 */
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";
import { Button, buttonClasses } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** Subtle nav link: muted, hovers to primary. Used for the role-gated links. */
function NavLink({
  href,
  children,
}: {
  href: string;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      className="rounded-pill px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent-soft hover:text-primary"
    >
      {children}
    </Link>
  );
}

export function Header() {
  const { user, isAuthenticated, isInitializing, logout } = useAuth();
  // Truthy checks (strict ===) so a missing/undefined role never shows the links.
  // The backend role enum is "ADMIN" | "VENDOR" | "CUSTOMER"; each gated link below
  // only renders for its matching role.
  const isAdmin = user?.role === "ADMIN";
  const isVendor = user?.role === "VENDOR";

  return (
    // Sticky container: floats above page content. The inner island is the
    // bright card; the outer sticky wrapper provides the top offset + bg blur.
    <div className="sticky top-0 z-40 bg-background/80 backdrop-blur-md">
      {/*
        The nav "island" — a bright rounded card with a soft shadow, centered
        with the rest of the page content and separated from the top edge.
        `flex-wrap` so on a narrow phone (~375px) the auth controls wrap below
        the brand instead of overflowing horizontally.
      */}
      <nav
        className={cn(
          "mx-auto mt-3 flex max-w-5xl flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-2xl border border-border/60 bg-card px-4 py-3 shadow-island sm:px-6",
        )}
        aria-label="Primary"
      >
        {/* Brand wordmark with a small dot mark for a friendlier feel. */}
        <Link
          href="/"
          className="flex items-center gap-2 font-semibold tracking-tight text-foreground transition-colors hover:text-primary"
        >
          <span
            aria-hidden="true"
            className="inline-block h-2.5 w-2.5 rounded-pill bg-primary shadow-island"
          />
          Service Marketplace
        </Link>

        {/* While boot-rehydrating, render no auth controls to avoid a logged-out flicker. */}
        {isInitializing ? null : isAuthenticated ? (
          // Authed row: role-gated nav links + account name + Log out.
          // `flex-wrap` here too: name + "My bookings" + "Admin" + "Log out" can wrap on
          // very small screens. `items-center` keeps them vertically aligned.
          <div className="flex flex-wrap items-center justify-end gap-x-1 gap-y-1 text-sm">
            <NavLink href="/bookings">My bookings</NavLink>
            {/*
              Admin link — only for ADMIN-role users. Non-admins never see the entry
              point, and even if they typed the URL directly, RequireAuth requireRole +
              the server's 403 would stop them. Gating the link is UX, not security.
            */}
            {isAdmin ? <NavLink href="/admin/vendors">Admin</NavLink> : null}
            {/*
              Vendor links — only for VENDOR-role users. Direct links mirror how
              "My bookings" is a direct link: dashboard overview, service management,
              and incoming bookings. Same gating rationale as Admin: UX, not security —
              RequireAuth requireRole="VENDOR" + the server's 403 enforce.
            */}
            {isVendor ? (
              <>
                <NavLink href="/vendor/dashboard">Dashboard</NavLink>
                <NavLink href="/vendor/services">My services</NavLink>
                <NavLink href="/vendor/bookings">Bookings</NavLink>
              </>
            ) : null}
            <span className="px-2 text-muted-foreground">
              {user?.fullName || user?.email || "Account"}
            </span>
            <Button variant="ghost" size="sm" onClick={logout}>
              Log out
            </Button>
          </div>
        ) : (
          // Logged-out row: Log in (ghost) + Sign up (primary).
          // Real navigation stays a real <Link> (accessible, middle-clickable);
          // `buttonClasses` styles it exactly like a Button of that variant/size.
          <div className="flex flex-wrap items-center justify-end gap-x-2 gap-y-1 text-sm">
            <Link
              href="/login"
              className={buttonClasses({ variant: "ghost", size: "sm" })}
            >
              Log in
            </Link>
            <Link
              href="/register"
              className={buttonClasses({ variant: "primary", size: "sm" })}
            >
              Sign up
            </Link>
          </div>
        )}
      </nav>
    </div>
  );
}
