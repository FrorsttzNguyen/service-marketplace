package com.hien.marketplace.application.exception;

/**
 * Exception thrown when a business rule is violated.
 *
 * WHY: Domain-specific validation errors that aren't simple input validation.
 * Used when: booking status transition invalid, order cannot be created from non-confirmed booking,
 * vendor not approved, insufficient permissions for operation.
 *
 * Example: Trying to cancel a COMPLETED booking → BusinessRuleViolationException
 * → 422 Unprocessable Entity with message explaining the rule
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String rule;
    private final String violation;

    public BusinessRuleViolationException(String rule, String violation) {
        super(String.format("Business rule violated: %s. %s", rule, violation));
        this.rule = rule;
        this.violation = violation;
    }

    public String getRule() {
        return rule;
    }

    public String getViolation() {
        return violation;
    }
}