package com.hien.marketplace.application.exception;

import java.time.LocalDateTime;

/**
 * Exception thrown when a booking conflicts with an existing booking.
 *
 * WHY: Double-booking prevention is a critical business rule.
 * This exception provides clear context about the conflict.
 *
 * Thrown when:
 * - Customer tries to book a time slot that's already booked
 * - Two concurrent requests try to book the same slot
 */
public class BookingConflictException extends RuntimeException {

    private final Long serviceId;
    private final LocalDateTime conflictingTime;

    public BookingConflictException(Long serviceId, LocalDateTime conflictingTime) {
        super(String.format("Booking conflict: service %d is already booked at %s",
                serviceId, conflictingTime));
        this.serviceId = serviceId;
        this.conflictingTime = conflictingTime;
    }

    public BookingConflictException(Long serviceId, LocalDateTime conflictingTime, String message) {
        super(message);
        this.serviceId = serviceId;
        this.conflictingTime = conflictingTime;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public LocalDateTime getConflictingTime() {
        return conflictingTime;
    }
}