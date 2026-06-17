"use client";

/**
 * Auth context — React mirror of the framework-agnostic `token-store`.
 *
 * The token-store module owns the actual tokens (in-memory access token + localStorage
 * refresh token) so the 401-retry in `client.ts` can read them outside React. This
 * context is the thin React layer: it re-renders components when auth state changes
 * (via subscribe()), exposes a clean `useAuth()` API, and runs the initial rehydrate.
 *
 * Why mirror instead of React state: `client.ts` needs the access token synchronously
 * on every request, including from non-React code. If the token lived only in React
 * state, every request would have to thread it through. Keeping it in a module + a
 * subscribe() API is the standard "external store → React" pattern (like Redux/Zustand).
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  applySession,
  clearSession,
  getUser,
  isAuthenticated,
  rehydrate,
  subscribe,
} from "./token-store";
import { login as loginEndpoint, register as registerEndpoint } from "./auth-api";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
} from "./auth-api";
import type { AuthUser } from "./token-store";

/** Snapshot of auth state exposed to consumers. */
interface AuthState {
  /** Non-secret profile fields, or null when logged out / not yet rehydrated. */
  user: AuthUser | null;
  /** True once a session is loaded. Note: the access token is NOT exposed — by design. */
  isAuthenticated: boolean;
  /** True while the initial rehydrate (refresh-on-boot) is running. */
  isInitializing: boolean;
}

/** Actions exposed by useAuth(). */
interface AuthActions {
  /**
   * Log in with email + password. On success, stores tokens + sets the user.
   * Throws ApiError on failure (401 = bad creds) so the form can map the message.
   */
  login: (body: LoginRequest) => Promise<void>;
  /**
   * Register a new account. On success, stores tokens + sets the user.
   * Throws ApiError on failure (409 = email taken, 400 = invalid).
   */
  register: (body: RegisterRequest) => Promise<void>;
  /** Clear all tokens + user state. */
  logout: () => void;
}

type AuthContextValue = AuthState & AuthActions;

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Bump counter used to force a re-render whenever the token-store emits. Because the
 * store is external, React won't see changes on its own; subscribing + bumping state
 * is how we bridge that. (The snapshot fields below are what consumers read.)
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [, setTick] = useState(0);
  const [isInitializing, setIsInitializing] = useState(true);

  // Re-render on any store change.
  useEffect(() => {
    const unsubscribe = subscribe(() => setTick((t) => t + 1));
    return unsubscribe;
  }, []);

  // On mount: try to restore a session from the stored refresh token.
  useEffect(() => {
    let active = true;
    (async () => {
      await rehydrate();
      if (active) setIsInitializing(false);
    })();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (body: LoginRequest) => {
    const res: AuthResponse = await loginEndpoint(body);
    applySession(res);
  }, []);

  const register = useCallback(async (body: RegisterRequest) => {
    const res: AuthResponse = await registerEndpoint(body);
    applySession(res);
  }, []);

  const logout = useCallback(() => {
    clearSession();
  }, []);

  const value: AuthContextValue = {
    user: getUser(),
    isAuthenticated: isAuthenticated(),
    isInitializing,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Read auth state + actions. Must be used inside <AuthProvider>. Throws if used
 * outside so misuse fails loudly instead of silently rendering as logged-out.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an <AuthProvider>");
  }
  return ctx;
}
