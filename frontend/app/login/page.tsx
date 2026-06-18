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
 *
 * Visual (Phase 7): a single centered island (narrow container) holding the title +
 * error + form + footer link. Inputs use the Input/Label/FieldError primitives; the
 * submit button is a full-width primary Button with a loading state.
 */
import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { ErrorState } from "@/components/error-state";
import { validateEmail } from "@/lib/auth/validation";
import { Container } from "@/components/ui/container";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/ui/field-error";

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
    return <ErrorState error={submitError} title="Couldn't log in." />;
  }

  return (
    <Container width="narrow">
      <Card padded>
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-foreground">
          Log in
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
          <div>
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={!!errors.email}
            />
            <FieldError>{errors.email}</FieldError>
          </div>

          <div>
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!errors.password}
            />
            <FieldError>{errors.password}</FieldError>
          </div>

          <Button type="submit" fullWidth isLoading={isSubmitting}>
            {isSubmitting ? "Logging in…" : "Log in"}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{" "}
          <Link
            href="/register"
            className="font-medium text-primary hover:underline"
          >
            Sign up
          </Link>
        </p>
      </Card>
    </Container>
  );
}
