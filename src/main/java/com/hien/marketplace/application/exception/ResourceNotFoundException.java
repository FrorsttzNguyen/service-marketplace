package com.hien.marketplace.application.exception;

/**
 * Exception thrown when a requested resource is not found in the database.
 *
 * WHY: Domain-specific exception for 404 Not Found responses.
 * Used when: user, service, booking, order, review, vendor not found by ID.
 *
 * Example: GET /api/services/999 → ResourceNotFoundException("Service", 999)
 * → 404 response with message "Service not found with id: 999"
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object identifier;

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(String.format("%s not found with id: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getIdentifier() {
        return identifier;
    }
}