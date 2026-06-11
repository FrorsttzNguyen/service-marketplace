package com.hien.marketplace.application.exception;

/**
 * Exception thrown when authentication fails (invalid credentials, expired token).
 *
 * WHY: Security-specific exception for 401 Unauthorized responses.
 * Used when: login fails, JWT token expired, invalid token signature.
 *
 * Note: Different from AccessDeniedException (403 Forbidden).
 * Authentication = "who are you?" (401)
 * Authorization = "are you allowed?" (403)
 */
public class AuthenticationException extends RuntimeException {

    private final String reason;

    public AuthenticationException(String reason) {
        super("Authentication failed: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}