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
 * Vendors view bookings for their services.
 *
 * Authorization:
 * - Customers: create, view own bookings, cancel
 * - Vendors: view bookings for their services
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
     * Get vendor's received bookings.
     */
    @GetMapping("/vendor")
    @Operation(
            summary = "Get vendor bookings",
            description = "List all bookings for the authenticated vendor's services",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bookings retrieved")
            }
    )
    public ResponseEntity<Page<BookingResponse>> getVendorBookings(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BookingResponse> bookings = bookingService.getVendorBookings(principal.userId(), pageable);
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

    // ================================================================
    // Vendor Booking Management Endpoints
    // ================================================================

    /**
     * Confirm booking (Vendor action).
     *
     * WHY: Vendor confirms a pending booking, changing status from PENDING to CONFIRMED.
     * Only vendor who owns the service can confirm.
     */
    @PutMapping("/{id}/confirm")
    @Operation(
            summary = "Confirm booking",
            description = "Vendor confirms a pending booking",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Booking confirmed"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Cannot confirm (not your service or invalid status)")
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
     * Start service (Vendor action).
     *
     * WHY: Vendor marks a confirmed booking as IN_PROGRESS when service begins.
     * Transition: CONFIRMED → IN_PROGRESS
     */
    @PutMapping("/{id}/start")
    @Operation(
            summary = "Start service",
            description = "Vendor marks a confirmed booking as in progress",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service started"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Cannot start (not your service or invalid status)")
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
     * Complete service (Vendor action).
     *
     * WHY: Vendor marks an in-progress booking as COMPLETED after service finishes.
     * Transition: IN_PROGRESS → COMPLETED
     */
    @PutMapping("/{id}/complete")
    @Operation(
            summary = "Complete service",
            description = "Vendor marks an in-progress booking as completed",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Service completed"),
                    @ApiResponse(responseCode = "404", description = "Booking not found"),
                    @ApiResponse(responseCode = "422", description = "Cannot complete (not your service or invalid status)")
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