/**
 * Badge — a status pill with a consistent color map.
 *
 * Replaces the three near-identical inline `statusBadgeClass` maps that were
 * copy-pasted across booking-card, vendor/bookings, vendor/services, and
 * admin/vendors. One place to define what each status looks like.
 *
 * Two shapes:
 *   - `<Badge tone="…">` for an arbitrary tone (subtle/filled/solid + color).
 *   - `<BookingStatusBadge status="PENDING" />` and
 *     `<ServiceStatusBadge status="DRAFT" />` for the known app enums, so call
 *     sites don't re-derive the tone themselves.
 *
 * All tones use soft tints with readable foreground (the design language's
 * "cheerful but readable" rule) and adapt to dark mode automatically via the
 * `dark:` variants.
 */
import { type ReactNode } from "react";
import { cn } from "@/lib/utils";

export type BadgeTone =
  | "neutral"
  | "primary"
  | "success"
  | "warning"
  | "danger"
  | "info";

const TONE_CLASSES: Record<BadgeTone, string> = {
  // Neutral — terminal/quiet statuses (CANCELLED, INACTIVE).
  neutral:
    "bg-neutral-100 text-neutral-700 dark:bg-neutral-800/80 dark:text-neutral-300",
  // Primary — the brand accent (default info-ish usage).
  primary:
    "bg-primary/10 text-primary dark:bg-primary/20 dark:text-primary",
  // Success — COMPLETED / ACTIVE / APPROVED / SUCCEEDED.
  success:
    "bg-green-100 text-green-800 dark:bg-green-950/50 dark:text-green-300",
  // Warning — PENDING / DRAFT / PROCESSING.
  warning:
    "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300",
  // Danger — REJECTED / FAILED.
  danger: "bg-red-100 text-red-800 dark:bg-red-950/50 dark:text-red-300",
  // Info — CONFIRMED / IN_PROGRESS (cool indigo tones).
  info: "bg-indigo-100 text-indigo-800 dark:bg-indigo-950/50 dark:text-indigo-300",
};

interface BadgeProps {
  tone?: BadgeTone;
  className?: string;
  children: ReactNode;
}

export function Badge({ tone = "neutral", className, children }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-pill px-2.5 py-0.5 text-xs font-semibold tracking-wide",
        TONE_CLASSES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/* --------------------------- App-specific badges --------------------------- */

import type { BookingStatus } from "@/lib/api/bookings";
import type { ServiceStatus } from "@/lib/api/vendor-services";
import type { VendorVerificationStatus } from "@/lib/api/admin";

/** Booking lifecycle → badge tone. */
const BOOKING_TONE: Record<BookingStatus, BadgeTone> = {
  PENDING: "warning",
  CONFIRMED: "info",
  PAID: "primary",
  IN_PROGRESS: "info",
  COMPLETED: "success",
  CANCELLED: "neutral",
};

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  return <Badge tone={BOOKING_TONE[status] ?? "neutral"}>{status}</Badge>;
}

/** Service lifecycle → badge tone. */
const SERVICE_TONE: Record<ServiceStatus, BadgeTone> = {
  DRAFT: "warning",
  ACTIVE: "success",
  INACTIVE: "neutral",
};

export function ServiceStatusBadge({ status }: { status: ServiceStatus }) {
  return <Badge tone={SERVICE_TONE[status] ?? "neutral"}>{status}</Badge>;
}

/** Vendor verification → badge tone. */
const VENDOR_TONE: Record<VendorVerificationStatus, BadgeTone> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger",
};

export function VendorStatusBadge({
  status,
}: {
  status: VendorVerificationStatus;
}) {
  return <Badge tone={VENDOR_TONE[status] ?? "neutral"}>{status}</Badge>;
}
