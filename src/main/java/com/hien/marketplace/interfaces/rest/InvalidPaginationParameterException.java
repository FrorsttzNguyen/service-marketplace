package com.hien.marketplace.interfaces.rest;

/**
 * Exception thrown when HTTP pagination parameters are invalid.
 *
 * WHY: Pagination validation is a REST/API concern, not a domain rule.
 * Using a dedicated exception keeps GlobalExceptionHandler from catching every
 * IllegalArgumentException and accidentally hiding programmer errors as 400s.
 */
public class InvalidPaginationParameterException extends RuntimeException {

    public InvalidPaginationParameterException(String message) {
        super(message);
    }
}
