package com.hien.marketplace.application.exception;

/**
 * Exception thrown when a requested resource is not found in the database.
 *
 * WHY: Domain-specific exception for 404 Not Found responses.
 * Used when: user, service, booking, order, review, provider not found by ID.
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

    /**
     * Constructor for "not found by field name".
     * Example: ResourceNotFoundException("Payment", "stripePaymentIntentId", "pi_xxx")
     */
    public ResourceNotFoundException(String resourceType, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: %s", resourceType, fieldName, fieldValue));
        this.resourceType = resourceType;
        this.identifier = fieldValue;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getIdentifier() {
        return identifier;
    }
}