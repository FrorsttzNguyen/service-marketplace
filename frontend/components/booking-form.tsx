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
 *
 * Visual (Phase 7): the form is its own island below the article; auth-gate CTA is
 * a smaller, calmer island. Inputs use the Input/Textarea/Label/FieldError primitives.
 */
import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { useCreateBooking } from "@/lib/api/bookings-queries";
import { ApiError } from "@/lib/api/client";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/ui/field-error";

interface BookingFormProps {
  serviceId: number;
}

/** Fields tracked for client-side validation. */
interface FieldErrors {
  startTime?: string;
  endTime?: string;
  quantity?: string;
  street?: string;
  city?: string;
}

export function BookingForm({ serviceId }: BookingFormProps) {
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const createBooking = useCreateBooking();

  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [street, setStreet] = useState("");
  const [city, setCity] = useState("");
  const [zipCode, setZipCode] = useState("");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<unknown>(null);

  // --- Auth gate: logged-out users get a sign-in CTA, not the form. ------------
  if (!isAuthenticated) {
    const redirect = `/services/${serviceId}`;
    return (
      <Card padded>
        <h2 className="text-lg font-semibold text-foreground">Book this home service</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          You need an account to book.{" "}
          <Link
            href={`/login?redirect=${encodeURIComponent(redirect)}`}
            className="font-medium text-primary hover:underline"
          >
            Log in
          </Link>{" "}
          or{" "}
          <Link
            href={`/register?redirect=${encodeURIComponent(redirect)}`}
            className="font-medium text-primary hover:underline"
          >
            sign up
          </Link>
          .
        </p>
      </Card>
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

    if (!street.trim()) {
      next.street = "Street is required.";
    }

    if (!city.trim()) {
      next.city = "City is required.";
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
      street: street.trim(),
      city: city.trim(),
      zipCode: zipCode.trim() ? zipCode.trim() : undefined,
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
    <Card padded>
      <h2 className="text-lg font-semibold text-foreground">Book this home service</h2>

      {submitError ? (
        <div
          className="mt-4 rounded-2xl border border-danger/30 bg-danger/10 p-4 text-sm text-danger"
          role="alert"
        >
          {describeSubmitError(submitError)}
        </div>
      ) : null}

      <form onSubmit={handleSubmit} className="mt-5 space-y-5" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Label htmlFor="startTime">Start time</Label>
            <Input
              id="startTime"
              type="datetime-local"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              aria-invalid={!!errors.startTime}
            />
            <FieldError>{errors.startTime}</FieldError>
          </div>

          <div>
            <Label htmlFor="endTime">End time</Label>
            <Input
              id="endTime"
              type="datetime-local"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              aria-invalid={!!errors.endTime}
            />
            <FieldError>{errors.endTime}</FieldError>
          </div>
        </div>

        <div>
          <Label htmlFor="quantity">Quantity</Label>
          <Input
            id="quantity"
            type="number"
            min={1}
            step={1}
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            aria-invalid={!!errors.quantity}
          />
          <FieldError>{errors.quantity}</FieldError>
        </div>

        <div className="grid gap-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <div>
            <Label htmlFor="street">Service street</Label>
            <Input
              id="street"
              value={street}
              onChange={(e) => setStreet(e.target.value)}
              aria-invalid={!!errors.street}
            />
            <FieldError>{errors.street}</FieldError>
          </div>

          <div>
            <Label htmlFor="city">Service city</Label>
            <Input
              id="city"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              aria-invalid={!!errors.city}
            />
            <FieldError>{errors.city}</FieldError>
          </div>
        </div>

        <div>
          <Label htmlFor="zipCode" hint="(optional)">
            Zip code
          </Label>
          <Input
            id="zipCode"
            value={zipCode}
            onChange={(e) => setZipCode(e.target.value)}
          />
        </div>

        <div>
          <Label htmlFor="notes" hint="(optional)">
            Notes
          </Label>
          <Textarea
            id="notes"
            rows={3}
            maxLength={1000}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
          <p className="mt-1.5 text-xs text-muted-foreground">
            Up to 1000 characters.
          </p>
        </div>

        <Button type="submit" isLoading={createBooking.isPending}>
          {createBooking.isPending ? "Booking…" : "Request booking"}
        </Button>
      </form>
    </Card>
  );
}
