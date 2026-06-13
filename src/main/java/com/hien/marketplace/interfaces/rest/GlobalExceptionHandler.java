package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.exception.AuthenticationException;
import com.hien.marketplace.application.exception.BookingConflictException;
import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.DuplicateResourceException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.interfaces.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for all REST controllers.
 *
 * WHY @RestControllerAdvice:
 * - Catches exceptions from ANY controller method
 * - Converts exceptions to consistent JSON error responses
 * - Avoids try-catch blocks in controllers
 * - Centralizes error handling logic
 *
 * Exception → HTTP Status Mapping:
 * - ResourceNotFoundException → 404 Not Found
 * - DuplicateResourceException → 409 Conflict
 * - BookingConflictException → 409 Conflict
 * - BusinessRuleViolationException → 422 Unprocessable Entity
 * - AuthenticationException → 401 Unauthorized
 * - AccessDeniedException → 403 Forbidden
 * - MethodArgumentNotValidException → 400 Bad Request (validation errors)
 * - ConstraintViolationException → 400 Bad Request (validation errors)
 * - Exception → 500 Internal Server Error (catch-all)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle 404 Not Found - resource not found in database.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("Resource not found: {} with id {} - {}",
                ex.getResourceType(), ex.getIdentifier(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "RESOURCE_NOT_FOUND",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle 409 Conflict - duplicate resource (email already exists, etc).
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request
    ) {
        log.warn("Duplicate resource: {} - {} = {} - {}",
                ex.getResourceType(), ex.getField(), ex.getValue(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "DUPLICATE_RESOURCE",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle 409 Conflict - booking time slot already taken.
     */
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ErrorResponse> handleBookingConflict(
            BookingConflictException ex,
            HttpServletRequest request
    ) {
        log.warn("Booking conflict for service {} at {} - {}",
                ex.getServiceId(), ex.getConflictingTime(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "BOOKING_CONFLICT",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle 422 Unprocessable Entity - business rule violation.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(
            BusinessRuleViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("Business rule violated: {} - {} - {}",
                ex.getRule(), ex.getViolation(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "BUSINESS_RULE_VIOLATION",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handle 422 Unprocessable Entity - illegal state transition.
     *
     * WHY: Domain entities throw IllegalStateException for invalid state transitions
     * (e.g., trying to confirm a CANCELLED booking). We map this to 422 to provide
     * clear error messages to clients.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request
    ) {
        log.warn("Invalid state transition: {} - {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "BUSINESS_RULE_VIOLATION",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handle 401 Unauthorized - authentication failed.
     */
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        log.warn("Authentication failed: {} - {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "AUTHENTICATION_FAILED",
                "Invalid email or password"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle 403 Forbidden - access denied (authenticated but not authorized).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("Access denied: {} - {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                "ACCESS_DENIED",
                "You don't have permission to access this resource"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle 400 Bad Request - Jakarta Bean Validation failed on @RequestBody.
     *
     * WHY: MethodArgumentNotValidException is thrown when @Valid fails on request body.
     * We extract field-level errors and return them in "details" field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        log.warn("Validation failed for {} - {}", request.getRequestURI(), ex.getMessage());

        // Extract field errors into Map<fieldName, errorMessage>
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse response = ErrorResponse.of(
                "VALIDATION_ERROR",
                "Invalid input data",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle 400 Bad Request - Jakarta Bean Validation failed on path/query params.
     *
     * WHY: ConstraintViolationException is thrown when @Valid fails on
     * path variables (@PathVariable) or query parameters (@RequestParam).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        log.warn("Constraint violation for {} - {}", request.getRequestURI(), ex.getMessage());

        Map<String, String> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            violations.put(propertyPath, message);
        });

        ErrorResponse response = ErrorResponse.of(
                "VALIDATION_ERROR",
                "Invalid input data",
                violations
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle 500 Internal Server Error - catch-all for unexpected exceptions.
     *
     * WHY: Last resort handler. Logs full stack trace for debugging
     * but returns generic message to client (don't leak internal details).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected error for {} - {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handle 400 Bad Request - invalid property reference in sort parameter.
     *
     * WHY: Spring Data throws PropertyReferenceException when client sends
     * invalid sort field (e.g., sort=nonexistentField,asc).
     * We convert this to 400 Bad Request instead of 500 Internal Server Error.
     */
    @ExceptionHandler(org.springframework.data.mapping.PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPropertyReference(
            org.springframework.data.mapping.PropertyReferenceException ex,
            HttpServletRequest request
    ) {
        log.warn("Invalid sort property for {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                "INVALID_SORT_PROPERTY",
                "Invalid sort field. Please check the field name and try again."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle 400 Bad Request - invalid pagination parameters.
     *
     * WHY: ValidatingPageableResolver throws this dedicated REST exception when
     * clients send invalid page/size values. We avoid catching every
     * IllegalArgumentException because unrelated programmer errors should remain 500s.
     */
    @ExceptionHandler(InvalidPaginationParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaginationParameter(
            InvalidPaginationParameterException ex,
            HttpServletRequest request
    ) {
        log.warn("Invalid pagination parameter for {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                "INVALID_PAGINATION_PARAMETER",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}