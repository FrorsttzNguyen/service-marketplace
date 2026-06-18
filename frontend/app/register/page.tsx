"use client";

/**
 * Register page — `POST /api/auth/register`.
 *
 * Controlled form with client validation mirroring the backend rules (password
 * complexity, phone E.164 pattern, name length, email format). The server response
 * is still the source of truth. Status mapping:
 *   - 409 → "email already registered"
 *   - 400 → server message (usually a field-validation detail)
 *   - other ApiError → its message; network/CORS → shared error card with hint.
 *
 * On success, redirect to `?redirect=` target or the catalog.
 *
 * Visual (Phase 7): a single centered island (narrow container) with the form fields
 * and a checkbox row for "register as vendor". Inputs use the Input/Label/FieldError
 * primitives; the submit button is a full-width primary Button.
 */
import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { ErrorState } from "@/components/error-state";
import {
  validateEmail,
  validateFullName,
  validatePassword,
  validatePhoneNumber,
} from "@/lib/auth/validation";
import { Container } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/ui/field-error";

interface FieldErrors {
  fullName?: string;
  email?: string;
  password?: string;
  phoneNumber?: string;
}

/**
 * Page entry — wraps the form in a Suspense boundary (the form uses useSearchParams(),
 * which is dynamic; the boundary lets Next prerender the static shell).
 */
export default function RegisterPage() {
  return (
    <Suspense fallback={null}>
      <RegisterForm />
    </Suspense>
  );
}

function RegisterForm() {
  const { register } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [registerAsVendor, setRegisterAsVendor] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);

    // Validate every field client-side first; collect ALL errors so the user can fix
    // them in one pass rather than one round-trip at a time.
    const next: FieldErrors = {
      fullName: validateFullName(fullName) ?? undefined,
      email: validateEmail(email) ?? undefined,
      password: validatePassword(password) ?? undefined,
      phoneNumber: validatePhoneNumber(phoneNumber) ?? undefined,
    };
    setErrors(next);
    if (Object.values(next).some(Boolean)) return;

    setIsSubmitting(true);
    register({
      fullName: fullName.trim(),
      email: email.trim(),
      password,
      phoneNumber: phoneNumber.trim(),
      registerAsVendor,
    })
      .then(() => {
        const redirect = searchParams.get("redirect");
        router.push(redirect && redirect.startsWith("/") ? redirect : "/");
      })
      .catch((err: unknown) => {
        setSubmitError(err);
      })
      .finally(() => setIsSubmitting(false));
  }

  function renderSubmitError(): React.ReactNode {
    if (submitError instanceof ApiError) {
      if (submitError.status === 409) return "That email is already registered.";
      return submitError.message; // 400 etc.
    }
    return <ErrorState error={submitError} title="Couldn't register." />;
  }

  return (
    <Container width="narrow">
      <Card padded>
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-foreground">
          Create an account
        </h1>

        {submitError && !(submitError instanceof ApiError) ? (
          <div className="mb-6">{renderSubmitError()}</div>
        ) : submitError ? (
          <div
            className="mb-6 rounded-2xl border border-danger/30 bg-danger/10 p-4 text-sm text-danger"
            role="alert"
          >
            {renderSubmitError()}
          </div>
        ) : null}

        <form onSubmit={handleSubmit} className="space-y-5" noValidate>
          <FieldInput
            id="fullName"
            label="Full name"
            type="text"
            autoComplete="name"
            value={fullName}
            onChange={setFullName}
            error={errors.fullName}
          />
          <FieldInput
            id="email"
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={setEmail}
            error={errors.email}
          />
          <FieldInput
            id="password"
            label="Password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={setPassword}
            error={errors.password}
            help="At least 8 characters with an uppercase letter, a lowercase letter, and a digit."
          />
          <FieldInput
            id="phoneNumber"
            label="Phone number"
            type="tel"
            autoComplete="tel"
            value={phoneNumber}
            onChange={setPhoneNumber}
            error={errors.phoneNumber}
            help="E.164 format, e.g. +14155550123."
          />

          {/* Vendor opt-in checkbox. Kept as a native checkbox inside a tinted
              pill so it reads as a single affordance distinct from the inputs. */}
          <label className="flex cursor-pointer items-center gap-3 rounded-2xl bg-muted/60 p-3 text-sm">
            <input
              type="checkbox"
              checked={registerAsVendor}
              onChange={(e) => setRegisterAsVendor(e.target.checked)}
              className="h-4 w-4 rounded accent-[rgb(var(--primary))]"
            />
            <span className="font-medium text-foreground">
              Register as a service provider (vendor)
            </span>
          </label>

          <Button type="submit" fullWidth isLoading={isSubmitting}>
            {isSubmitting ? "Creating account…" : "Create account"}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-medium text-primary hover:underline"
          >
            Log in
          </Link>
        </p>
      </Card>
    </Container>
  );
}

/** Small wrapper for a labeled text input with optional help + error text. */
function FieldInput({
  id,
  label,
  type,
  autoComplete,
  value,
  onChange,
  error,
  help,
}: {
  id: string;
  label: string;
  type: string;
  autoComplete: string;
  value: string;
  onChange: (v: string) => void;
  error?: string;
  help?: string;
}) {
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={!!error}
      />
      {help && !error ? (
        <p className="mt-1.5 text-xs text-muted-foreground">{help}</p>
      ) : null}
      <FieldError>{error}</FieldError>
    </div>
  );
}
