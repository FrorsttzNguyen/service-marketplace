/**
 * Client-side validation rules for auth forms.
 *
 * These mirror the backend constraints in docs/api/openapi.yaml — good UX (instant
 * feedback) but NOT the source of truth. The server's response always wins; these
 * just cut down on round-trips for obviously-invalid input.
 *
 * Patterns are the same as the spec:
 *   - password: ≥8 chars + at least one lowercase, one uppercase, one digit
 *     (RegisterRequest.password.pattern)
 *   - phoneNumber: E.164-ish, ^\+?[1-9]\d{1,14}$ (RegisterRequest.phoneNumber.pattern)
 *   - fullName: 2–100 chars; email: non-empty (server does the real email validation).
 */

/** At least 8 chars with a lowercase, uppercase, and digit. */
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;
/** E.164-ish: optional +, then a non-zero digit and 1–14 more digits. */
export const PHONE_PATTERN = /^\+?[1-9]\d{1,14}$/;

export function validateEmail(email: string): string | null {
  if (!email.trim()) return "Email is required.";
  // Lightweight check; the backend does RFC-level validation.
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return "Enter a valid email address.";
  return null;
}

export function validatePassword(password: string): string | null {
  if (!password) return "Password is required.";
  if (password.length < 8) return "Password must be at least 8 characters.";
  if (!PASSWORD_PATTERN.test(password)) {
    return "Password needs an uppercase letter, a lowercase letter, and a digit.";
  }
  return null;
}

export function validateFullName(name: string): string | null {
  if (name.trim().length < 2) return "Name must be at least 2 characters.";
  if (name.trim().length > 100) return "Name must be at most 100 characters.";
  return null;
}

export function validatePhoneNumber(phone: string): string | null {
  if (!phone.trim()) return "Phone number is required.";
  if (!PHONE_PATTERN.test(phone.trim())) {
    return "Enter a valid phone (e.g. +14155550123).";
  }
  return null;
}
