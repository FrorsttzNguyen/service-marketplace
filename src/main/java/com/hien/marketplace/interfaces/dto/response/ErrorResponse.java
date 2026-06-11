package com.hien.marketplace.interfaces.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error response format for all API errors.
 *
 * WHY: Consistent error format makes client-side error handling predictable.
 * Clients can parse this structure regardless of which endpoint failed.
 *
 * Structure:
 * - code: Machine-readable error identifier (e.g., "VALIDATION_ERROR", "RESOURCE_NOT_FOUND")
 * - message: Human-readable summary (e.g., "Invalid input data")
 * - details: Field-level validation errors (only for 400 Bad Request)
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null fields (e.g., details may be absent)
public record ErrorResponse(
    String code,
    String message,
    Object details  // Can be Map<String, String> for validation errors, or null
) {
    /**
     * Factory method for simple errors without details.
     * Used for 401, 403, 404, 500, etc.
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    /**
     * Factory method for validation errors with field-level details.
     * Used for 400 Bad Request with Jakarta Validation failures.
     *
     * @param details Map of fieldName -> errorMessage
     */
    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(code, message, details);
    }
}