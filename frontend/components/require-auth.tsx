"use client";

/**
 * RequireAuth — route guard for protected subtrees.
 *
 * Behavior:
 *   - While `isInitializing` (the boot-time rehydrate is running), render a spinner.
 *     This prevents a bounce: a logged-in user reloading a protected page would
 *     momentarily appear logged-out until the refresh resolves.
 *   - Once initialized, if not authenticated → redirect to /login?redirect=<current
 *     path>, so login can send them back where they came from.
 *   - If authenticated → render the children.
 *
 * No protected route exists yet (Slice 3 adds bookings/orders). This component is
 * exported now so Slice 3 can wrap its pages directly:
 *   <RequireAuth><BookingsPage /></RequireAuth>
 */
import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { ServiceDetailSkeleton } from "@/components/skeletons";

interface RequireAuthProps {
  children: ReactNode;
  /** Optional role gate (e.g. "VENDOR"). If set, non-matching roles also redirect. */
  requireRole?: string;
}

export function RequireAuth({ children, requireRole }: RequireAuthProps) {
  const { isAuthenticated, isInitializing, user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    // Wait until the boot rehydrate finishes before deciding — otherwise we'd bounce
    // a logged-in user whose session hasn't restored yet.
    if (isInitializing) return;

    if (!isAuthenticated) {
      const redirect = pathname ? `?redirect=${encodeURIComponent(pathname)}` : "";
      router.replace(`/login${redirect}`);
      return;
    }

    if (requireRole && user?.role !== requireRole) {
      // Authenticated but wrong role → send home. (No admin UI yet; reserved for Slice 7.)
      router.replace("/");
    }
  }, [isAuthenticated, isInitializing, user, requireRole, router, pathname]);

  if (isInitializing) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-10">
        <ServiceDetailSkeleton />
      </main>
    );
  }

  if (!isAuthenticated) return null; // redirect in flight
  if (requireRole && user?.role !== requireRole) return null;

  return <>{children}</>;
}
