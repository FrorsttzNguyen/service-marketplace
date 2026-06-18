"use client";

/**
 * Site header / nav. Shows the brand link plus auth-aware controls:
 *   - Logged out → "Log in" + "Sign up" links.
 *   - Logged in  → the user's name + a "Log out" button.
 *   - Admin user → an extra "Admin" link to /admin/vendors.
 *   - Initializing → nothing (avoid a flash of logged-out controls during rehydrate).
 *
 * The access token is intentionally never read here — only the non-secret user
 * profile (name, role) from useAuth(), which mirrors the token-store.
 *
 * Role-gating the Admin link depends on `user.role` surviving a page reload, which in
 * turn depends on /me being called during rehydrate (see token-store.rehydrate).
 * Without that wiring, an admin reloading any page would briefly appear role-less and
 * the Admin link would flicker off; rehydrate now populates the role before this renders.
 */
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";

export function Header() {
  const { user, isAuthenticated, isInitializing, logout } = useAuth();
  // Truthy check (not a strict ===) so a missing/undefined role never shows the link.
  // The backend role enum is "ADMIN" | "VENDOR" | "CUSTOMER"; only ADMIN gets the link.
  const isAdmin = user?.role === "ADMIN";

  return (
    <header className="border-b border-neutral-200 dark:border-neutral-800">
      {/*
        The nav uses flex-wrap so on a narrow phone (~375px) the auth controls
        wrap below the brand instead of overflowing horizontally (which would
        cause a horizontal scrollbar or crammed, overlapping text). `gap-x-4`
        + `gap-y-2` keep spacing sane across both wrapped lines.
      */}
      <nav
        className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3"
        aria-label="Primary"
      >
        <Link
          href="/"
          className="font-semibold tracking-tight hover:text-blue-600 dark:hover:text-blue-400"
        >
          Service Marketplace
        </Link>

        {/* While boot-rehydrating, render no auth controls to avoid a logged-out flicker. */}
        {isInitializing ? null : isAuthenticated ? (
          // `flex-wrap` here too: name + "My bookings" + "Admin" + "Log out" can wrap on
          // very small screens. `items-center` keeps them vertically aligned.
          <div className="flex flex-wrap items-center justify-end gap-x-3 gap-y-1 text-sm">
            <Link
              href="/bookings"
              className="text-neutral-600 hover:text-blue-600 dark:text-neutral-400 dark:hover:text-blue-400"
            >
              My bookings
            </Link>
            {/*
              Admin link — only for ADMIN-role users. Non-admins never see the entry
              point, and even if they typed the URL directly, RequireAuth requireRole +
              the server's 403 would stop them. Gating the link is UX, not security.
            */}
            {isAdmin ? (
              <Link
                href="/admin/vendors"
                className="text-neutral-600 hover:text-blue-600 dark:text-neutral-400 dark:hover:text-blue-400"
              >
                Admin
              </Link>
            ) : null}
            <span className="text-neutral-600 dark:text-neutral-400">
              {user?.fullName || user?.email || "Account"}
            </span>
            <button
              type="button"
              onClick={logout}
              className="rounded border border-neutral-300 px-3 py-1 hover:border-blue-400 hover:text-blue-600 dark:border-neutral-700 dark:hover:text-blue-400"
            >
              Log out
            </button>
          </div>
        ) : (
          <div className="flex flex-wrap items-center justify-end gap-x-3 gap-y-1 text-sm">
            <Link
              href="/login"
              className="text-neutral-600 hover:text-blue-600 dark:text-neutral-400 dark:hover:text-blue-400"
            >
              Log in
            </Link>
            <Link
              href="/register"
              className="rounded bg-blue-600 px-3 py-1 font-medium text-white hover:bg-blue-700"
            >
              Sign up
            </Link>
          </div>
        )}
      </nav>
    </header>
  );
}
