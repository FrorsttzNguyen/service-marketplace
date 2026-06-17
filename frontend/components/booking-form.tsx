"use client";

/**
 * Booking form — creates a booking for a service via `POST /api/bookings`.
 *
 * Auth gate: rendered only on the detail page when the service loaded. If the user is
 * not authenticated, we show a call-to-action that routes to
 * /login?redirect=/services/<id> (so login can bounce them back). The authed branch
 * shows the actual form.
 *
 * datetime-local → ISO conversion: `<input type="datetime-local">` yields a string
 * like "2026-06-20T14:30" interpreted in the *user's local timezone*. `new Date(value)`
 * parses that into a JS Date at the right instant, and `.toISOString()` emits the UTC
 * ISO-8601 string the backend's date-time fields expect (e.g. "2026-06-20T06:30:00.000Z").
 * We never build ISO strings by hand — letting the Date object handle TZ math avoids
 * off-by-N-hours bugs.
 *
 * Client validation mirrors the backend: both times required, start not in the past,
 * end after start. The server response is still the source of truth.
 */
import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { useCreateBooking } from "@/lib/api/bookings-queries";
import { ApiError } from "@/lib/api/client";

interface BookingFormProps {
  serviceId: number;
}

/** Fields tracked for client-side validation. */
interface FieldErrors {
  startTime?: string;
  endTime?: string;
  quantity?: string;
}

export function BookingForm({ serviceId }: BookingFormProps) {
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const createBooking = useCreateBooking();

  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<unknown>(null);

  // --- Auth gate: logged-out users get a sign-in CTA, not the form. ------------
  if (!isAuthenticated) {
    const redirect = `/services/${serviceId}`;
    return (
      <div className="mt-8 rounded-lg border border-neutral-200 p-6 dark:border-neutral-800">
        <h2 className="text-lg font-semibold">Book this service</h2>
        <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
          You need an account to book.{" "}
          <Link
            href={`/login?redirect=${encodeURIComponent(redirect)}`}
            className="text-blue-600 hover:underline dark:text-blue-400"
          >
            Log in
          </Link>{" "}
          or{" "}
          <Link
            href={`/register?redirect=${encodeURIComponent(redirect)}`}
            className="text-blue-600 hover:underline dark:text-blue-400"
          >
            sign up
          </Link>
          .
        </p>
      </div>
    );
  }

  // --- Validation ------------------------------------------------------------

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    const now = new Date();

    if (!startTime) {
      next.startTime = "Start time is required.";
    } else {
      const start = new Date(startTime);
      if (Number.isNaN(start.getTime())) {
        next.startTime = "Enter a valid start time.";
      } else if (start.getTime() < now.getTime()) {
        next.startTime = "Start time can't be in the past.";
      }
    }

    if (!endTime) {
      next.endTime = "End time is required.";
    } else {
      const end = new Date(endTime);
      if (Number.isNaN(end.getTime())) {
        next.endTime = "Enter a valid end time.";
      } else if (startTime && end.getTime() <= new Date(startTime).getTime()) {
        next.endTime = "End time must be after the start time.";
      }
    }

    const qty = Number(quantity);
    if (!Number.isInteger(qty) || qty < 1) {
      next.quantity = "Quantity must be a whole number ≥ 1.";
    }

    return next;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);

    const fieldErrors = validate();
    setErrors(fieldErrors);
    if (Object.values(fieldErrors).some(Boolean)) return;

    // datetime-local → ISO: Date parses the local-time string into an absolute
    // instant, toISOString() emits UTC ISO-8601 the backend expects.
    const body = {
      serviceId,
      startTime: new Date(startTime).toISOString(),
      endTime: new Date(endTime).toISOString(),
      quantity: Number(quantity),
      notes: notes.trim() ? notes.trim() : undefined,
    };

    createBooking.mutate(body, {
      onSuccess: () => {
        router.push("/bookings");
      },
      onError: (err: unknown) => {
        setSubmitError(err);
      },
    });
  }

  /** Translate an ApiError status into a single user-facing line for this form. */
  function describeSubmitError(err: unknown): string {
    if (err instanceof ApiError) {
      if (err.status === 404) return "This service was not found.";
      if (err.status === 422)
        return "This service isn't available for those times.";
      // 400 etc. — use the parsed message, fall back to a generic line.
      return err.message;
    }
    return "Couldn't create the booking. Please try again.";
  }

  return (
    <div className="mt-8 rounded-lg border border-neutral-200 p-6 dark:border-neutral-800">
      <h2 className="text-lg font-semibold">Book this service</h2>

      {submitError ? (
        <p
          className="mt-3 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
          role="alert"
        >
          {describeSubmitError(submitError)}
        </p>
      ) : null}

      <form onSubmit={handleSubmit} className="mt-4 space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="startTime" className="mb-1 block text-sm font-medium">
              Start time
            </label>
            <input
              id="startTime"
              type="datetime-local"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
              aria-invalid={!!errors.startTime}
            />
            {errors.startTime ? (
              <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                {errors.startTime}
              </p>
            ) : null}
          </div>

          <div>
            <label htmlFor="endTime" className="mb-1 block text-sm font-medium">
              End time
            </label>
            <input
              id="endTime"
              type="datetime-local"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
              aria-invalid={!!errors.endTime}
            />
            {errors.endTime ? (
              <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                {errors.endTime}
              </p>
            ) : null}
          </div>
        </div>

        <div>
          <label htmlFor="quantity" className="mb-1 block text-sm font-medium">
            Quantity
          </label>
          <input
            id="quantity"
            type="number"
            min={1}
            step={1}
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
            aria-invalid={!!errors.quantity}
          />
          {errors.quantity ? (
            <p className="mt-1 text-sm text-red-600 dark:text-red-400">
              {errors.quantity}
            </p>
          ) : null}
        </div>

        <div>
          <label htmlFor="notes" className="mb-1 block text-sm font-medium">
            Notes <span className="text-neutral-400">(optional)</span>
          </label>
          <textarea
            id="notes"
            rows={3}
            maxLength={1000}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
          />
          <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
            Up to 1000 characters.
          </p>
        </div>

        <button
          type="submit"
          disabled={createBooking.isPending}
          className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {createBooking.isPending ? "Booking…" : "Request booking"}
        </button>
      </form>
    </div>
  );
}
