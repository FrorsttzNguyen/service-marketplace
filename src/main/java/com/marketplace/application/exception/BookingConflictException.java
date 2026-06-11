package com.hien.marketplace.application.exception;

/**
 * Exception thrown when a booking conflicts with an existing booking.
 *
 * WHY: Double-booking prevention requires proper error response.
 * Used when: customer tries to book a time slot that's already taken.
 *
 * Example: POST /api/bookings with overlapping time → BookingConflictException
 * → 409 Conflict response with message explaining the conflict
 */
public class BookingConflictException extends RuntimeException {

    private final Long serviceId;
    private final String conflictingTime;

    public BookingConflictException(Long serviceId, String conflictingTime) {
        super(String.format("Time slot conflict for service %d: %s is already booked", serviceId, conflictingTime));
        this.serviceId = serviceId;
        this.conflictingTime = conflictingTime;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public String getConflictingTime() {
        return conflictingTime;
    }
}