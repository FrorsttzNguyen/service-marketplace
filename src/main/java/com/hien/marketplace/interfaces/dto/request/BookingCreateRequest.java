package com.hien.marketplace.interfaces.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a booking.
 *
 * WHY: Customer books a service for a specific time slot.
 *
 * Validation:
 * - @Future: Start time must be in the future (can't book in the past)
 * - @NotBlank street/city: new home-service bookings need a real service location
 * - Service existence and availability checked in service layer
 */
public record BookingCreateRequest(
    @NotNull(message = "Service ID is required")
    Long serviceId,

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    LocalDateTime startTime,

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    LocalDateTime endTime,

    @Positive(message = "Quantity must be positive")
    Integer quantity,  // Number of units (e.g., 2 hours for hourly service)

    @NotBlank(message = "Street is required")
    String street,

    @NotBlank(message = "City is required")
    String city,

    String zipCode,

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    String notes  // Optional: customer notes for provider
) {
}
