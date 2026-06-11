package com.hien.marketplace.application.exception;

/**
 * Exception thrown when a duplicate resource is detected.
 *
 * WHY: Used for unique constraint violations that need user-friendly messages.
 * Used when: email already registered, phone number already used.
 *
 * Example: POST /api/auth/register with existing email → DuplicateResourceException
 * → 409 Conflict with message "Email already registered"
 */
public class DuplicateResourceException extends RuntimeException {

    private final String resourceType;
    private final String field;
    private final Object value;

    public DuplicateResourceException(String resourceType, String field, Object value) {
        super(String.format("%s already exists with %s: %s", resourceType, field, value));
        this.resourceType = resourceType;
        this.field = field;
        this.value = value;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }
}