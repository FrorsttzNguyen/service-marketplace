package com.hien.marketplace.application.exception;

/**
 * Exception thrown when Stripe API call fails.
 *
 * WHY: Wrap StripeException to provide application-specific exception.
 * Allows GlobalExceptionHandler to return appropriate HTTP status.
 *
 * Common causes:
 * - Invalid API key
 * - Network timeout
 * - Invalid request (e.g., negative amount)
 * - Rate limiting
 */
public class StripeApiException extends RuntimeException {

    private final String errorCode;

    public StripeApiException(String message) {
        super(message);
        this.errorCode = "STRIPE_API_ERROR";
    }

    public StripeApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "STRIPE_API_ERROR";
    }

    public StripeApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
