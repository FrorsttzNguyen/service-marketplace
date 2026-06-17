"use client";

/**
 * Login page — `POST /api/auth/login`.
 *
 * Controlled form with light client validation (email format + non-empty password),
 * but the server response is the source of truth. Status mapping:
 *   - 401 → "Invalid email or password" (don't reveal which is wrong).
 *   - 400 → show the server's message (validation error).
 *   - other ApiError → show its message; network/CORS failures get the CORS hint.
 *
 * On success, redirect to `?redirect=` target or the catalog.
 */
import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { ErrorState } from "@/components/error-state";
import { validateEmail } from "@/lib/auth/validation";

/**
 * Page entry — wraps the form in a Suspense boundary. This is required because the
 * form calls useSearchParams(), which is dynamic; the boundary lets Next prerender
 * the static shell and defer the search-params-aware part to the client.
 */
export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);

    // Client validation first — fail fast without a round-trip.
    const emailErr = validateEmail(email);
    const passwordErr = password ? null : "Password is required.";
    setErrors({ email: emailErr ?? undefined, password: passwordErr ?? undefined });
    if (emailErr || passwordErr) return;

    setIsSubmitting(true);
    login({ email: email.trim(), password })
      .then(() => {
        const redirect = searchParams.get("redirect");
        router.push(redirect && redirect.startsWith("/") ? redirect : "/");
      })
      .catch((err: unknown) => {
        setSubmitError(err);
      })
      .finally(() => setIsSubmitting(false));
  }

  // Translate an ApiError into a single human-readable line for this page.
  function renderSubmitError(): React.ReactNode {
    if (submitError instanceof ApiError) {
      if (submitError.status === 401) {
        // Never disclose whether the email vs password was wrong — mitigates enumeration.
        return "Invalid email or password.";
      }
      // 400 etc.: use the parsed message, or a sensible fallback.
      return submitError.message;
    }
    // Non-ApiError (network/CORS) → show the shared error card with the CORS hint.
    return (
      <ErrorState error={submitError} title="Couldn't log in." />
    );
  }

  return (
    <main className="mx-auto max-w-sm px-4 py-10">
      <h1 className="mb-6 text-2xl font-bold tracking-tight">Log in</h1>

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
        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium">
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
            aria-invalid={!!errors.email}
          />
          {errors.email ? (
            <p className="mt-1 text-sm text-red-600 dark:text-red-400">{errors.email}</p>
          ) : null}
        </div>

        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium">
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded border border-neutral-300 px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
            aria-invalid={!!errors.password}
          />
          {errors.password ? (
            <p className="mt-1 text-sm text-red-600 dark:text-red-400">{errors.password}</p>
          ) : null}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {isSubmitting ? "Logging in…" : "Log in"}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-neutral-600 dark:text-neutral-400">
        Don&apos;t have an account?{" "}
        <Link href="/register" className="text-blue-600 hover:underline dark:text-blue-400">
          Sign up
        </Link>
      </p>
    </main>
  );
}
