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
    <main className="mx-auto max-w-sm px-4 py-10">
      <h1 className="mb-6 text-2xl font-bold tracking-tight">Create an account</h1>

      {submitError && !(submitError instanceof ApiError) ? (
        <div className="mb-6">{renderSubmitError()}</div>
      ) : submitError ? (
        <p
          className="mb-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
          role="alert"
        >
          {renderSubmitError()}
        </p>
      ) : null}

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
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

        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={registerAsVendor}
            onChange={(e) => setRegisterAsVendor(e.target.checked)}
          />
          Register as a service provider (vendor)
        </label>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {isSubmitting ? "Creating account…" : "Create account"}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-neutral-600 dark:text-neutral-400">
        Already have an account?{" "}
        <Link href="/login" className="text-blue-600 hover:underline dark:text-blue-400">
          Log in
        </Link>
      </p>
    </main>
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
      <label htmlFor={id} className="mb-1 block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
        aria-invalid={!!error}
      />
      {help && !error ? (
        <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">{help}</p>
      ) : null}
      {error ? (
        <p className="mt-1 text-sm text-red-600 dark:text-red-400">{error}</p>
      ) : null}
    </div>
  );
}
