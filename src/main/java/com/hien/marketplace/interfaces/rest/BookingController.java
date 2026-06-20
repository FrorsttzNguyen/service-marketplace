package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.BookingService;
import com.hien.marketplace.infrastructure.security.JwtAuthenticationFilter;
import com.hien.marketplace.interfaces.dto.request.BookingCreateRequest;
import com.hien.marketplace.interfaces.dto.response.BookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for booking operations.
 *
 * WHY: Customers create and manage bookings.
 * Providers view bookings for their services.
 *
 * Authorization:
 * - Customers: create, view own bookings, cancel
 * - Providers: view bookings for their services
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Booking management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    /**
     * Create new booking (Customer).
     */
    @PostMapping
    @Operation(
            summary = "Create booking",
            description = "Create a new booking for a service",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Booking created"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "404", description = "Service not found"),
                    @ApiResponse(responseCode = "422", description = "Service not available")
            }
    )
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody BookingCreateRequest request
    ) {
        BookingResponse booking = bookingService.createBooking(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    /**
     * Get customer's bookings.
     */
    @GetMapping
    @Operation(
            summary = "Get my bookings",
            description = "List all bookings for the authenticated customer",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bookings retrieved")
            }
    )
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BookingResponse> bookings = bookingService.getCustomerBookings(principal.userId(), pageable);
        return ResponseEntity.ok(bookings);
    }

    /**
     * Get provider's received bookings.
     */
    @GetMapping("/provider")
    @Operation(
            summary = "Get provider bookings",
            description = "List all bookings for the authenticated provider's services",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bookings retrieved")
            }
    )
    public ResponseEntity<Page<BookingResponse>> getProviderBookings(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BookingResponse> bookings = bookingService.getProviderBookings(principal.userId(), pageable);
        return ResponseEntity.ok(bookings);
    }

    /**
     * Cancel booking.
     */
    @PutMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel booking",
            description = "Cancel a pending booking",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Booking cancelled"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Cannot cancel (not yours or invalid status)")
            }
    )
    public ResponseEntity<BookingResponse> cancelBooking(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        BookingResponse booking = bookingService.cancelBooking(principal.userId(), id);
        return ResponseEntity.ok(booking);
    }

    /**
     * Confirm booking (Provider action).
     *
     * WHY exposed here: a booking must be CONFIRMED before an Order can be created from it
     * (Phase 4 order glue). The service method BookingService.confirmBooking already implements
     * the provider ownership check + optimistic-locking retry + state machine transition — this
     * endpoint is just the REST wiring, mirroring cancelBooking's shape.
     */
    @PutMapping("/{id}/confirm")
    @Operation(
            summary = "Confirm booking",
            description = "Provider confirms a pending booking, transitioning it to CONFIRMED. " +
                    "Only the provider who owns the service may confirm. " +
                    "Required for the book → pay flow: an Order can only be created from a CONFIRMED booking.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Booking confirmed"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Not your service, or status doesn't allow confirm")
            }
    )
    public ResponseEntity<BookingResponse> confirmBooking(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        BookingResponse booking = bookingService.confirmBooking(principal.userId(), id);
        return ResponseEntity.ok(booking);
    }

    /**
     * Start service (Provider action).
     *
     * WHY exposed here: the booking lifecycle is PENDING -> CONFIRMED -> IN_PROGRESS -> COMPLETED.
     * A review can only be left on a COMPLETED booking, so the provider needs a way to advance a
     * CONFIRMED booking to IN_PROGRESS. The service method BookingService.startService already
     * implements the provider ownership check + state machine transition — this is just REST wiring,
     * mirroring confirmBooking's shape.
     */
    @PutMapping("/{id}/start")
    @Operation(
            summary = "Start service",
            description = "Provider marks a CONFIRMED booking as IN_PROGRESS when the service begins. " +
                    "Only the provider who owns the service may start it.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service started"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Not your service, or status doesn't allow start")
            }
    )
    public ResponseEntity<BookingResponse> startService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        BookingResponse booking = bookingService.startService(principal.userId(), id);
        return ResponseEntity.ok(booking);
    }

    /**
     * Complete service (Provider action).
     *
     * WHY exposed here: completing a booking is the precondition for the customer to leave a review.
     * BookingService.completeService already enforces provider ownership + the IN_PROGRESS -> COMPLETED
     * state machine transition — this endpoint is just the REST wiring.
     */
    @PutMapping("/{id}/complete")
    @Operation(
            summary = "Complete service",
            description = "Provider marks an IN_PROGRESS booking as COMPLETED once the service is done. " +
                    "Only the provider who owns the service may complete it. " +
                    "A booking must be COMPLETED before the customer can review it.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service completed"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Not your service, or status doesn't allow complete")
            }
    )
    public ResponseEntity<BookingResponse> completeService(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PathVariable Long id
    ) {
        BookingResponse booking = bookingService.completeService(principal.userId(), id);
        return ResponseEntity.ok(booking);
    }
}