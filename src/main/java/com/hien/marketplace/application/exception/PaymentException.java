package com.hien.marketplace.application.exception;

import com.hien.marketplace.domain.payment.PaymentStatus;

/**
 * Exception thrown when payment operation violates business rules.
 *
 * WHY: Separate payment-specific errors from general business rule violations.
 * Allows more specific error messages and HTTP status codes.
 *
 * Examples:
 * - Payment already exists for order
 * - Payment not in correct status for operation
 * - Refund amount exceeds payment amount
 */
public class PaymentException extends BusinessRuleViolationException {

    private final PaymentStatus currentStatus;

    public PaymentException(String field, String message) {
        super(field, message);
        this.currentStatus = null;
    }

    public PaymentException(String field, String message, PaymentStatus currentStatus) {
        super(field, message);
        this.currentStatus = currentStatus;
    }

    public PaymentStatus getCurrentStatus() {
        return currentStatus;
    }
}
