package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.BookingMapper;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.UserRepository;
import com.hien.marketplace.interfaces.dto.request.BookingCreateRequest;
import com.hien.marketplace.interfaces.dto.response.BookingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for booking operations.
 *
 * WHY: Booking is a complex use case with:
 * - Service availability validation
 * - Price calculation based on PricingType
 * - Status transitions (state machine)
 * - Optimistic locking (Phase 3)
 *
 * Phase 2 focuses on basic CRUD. Phase 3 adds:
 * - Time slot conflict detection
 * - Optimistic locking retry
 * - Full state machine implementation
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;

    /**
     * Create new booking.
     *
     * Flow:
     * 1. Validate service exists and is active
     * 2. Validate customer exists
     * 3. Calculate total price based on PricingType
     * 4. Create Booking entity
     * 5. Save and return response
     *
     * Note: Time slot conflict detection comes in Phase 3
     */
    @Transactional
    public BookingResponse createBooking(Long customerId, BookingCreateRequest request) {
        // Step 1: Validate service
        ServiceEntity service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", request.serviceId()));

        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Service availability",
                    "Service is not available for booking"
            );
        }

        // Step 2: Validate customer
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerId));

        // Step 3: Calculate total price
        Money totalPrice = calculateTotalPrice(service, request.quantity());

        // Step 4: Create Booking
        Booking booking = new Booking(
                service,
                customer,
                service.getVendor(),
                request.startTime().toLocalDate(),
                request.startTime().toLocalTime(),
                request.endTime().toLocalTime(),
                totalPrice
        );

        // Notes field would need setter - skip for Phase 2

        // Step 5: Save
        booking = bookingRepository.save(booking);

        return enrichBookingResponse(booking);
    }

    /**
     * Get bookings for customer.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getCustomerBookings(Long customerId, Pageable pageable) {
        return bookingRepository.findByCustomerId(customerId, pageable)
                .map(this::enrichBookingResponse);
    }

    /**
     * Get bookings for vendor.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getVendorBookings(Long vendorId, Pageable pageable) {
        return bookingRepository.findByVendorId(vendorId, pageable)
                .map(this::enrichBookingResponse);
    }

    /**
     * Cancel booking.
     *
     * WHY: Only PENDING bookings can be cancelled.
     * Status transition enforced by BookingStatus state machine.
     */
    @Transactional
    public BookingResponse cancelBooking(Long customerId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // Authorization: only customer who made the booking can cancel
        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new BusinessRuleViolationException(
                    "Booking ownership",
                    "You can only cancel your own bookings"
            );
        }

        // Domain method handles state machine validation
        // For customer cancellation, pass customer as changedBy and reason
        User customer = booking.getCustomer();
        booking.cancel(customer, "Cancelled by customer");

        booking = bookingRepository.save(booking);

        return enrichBookingResponse(booking);
    }

    // === Private helper methods ===

    private Money calculateTotalPrice(ServiceEntity service, Integer quantity) {
        int qty = quantity != null ? quantity : 1;

        // Strategy pattern: PricingType calculates price
        BigDecimal basePrice = BigDecimal.valueOf(
                service.getBasePrice().getAmountCents(), 2
        );

        BigDecimal total;
        if (service.getPricingType() == PricingType.HOURLY) {
            // Assume 1 unit = 1 hour for hourly pricing
            total = basePrice.multiply(BigDecimal.valueOf(qty));
        } else {
            // FIXED pricing: total = basePrice * quantity
            total = basePrice.multiply(BigDecimal.valueOf(qty));
        }

        return Money.of(total.multiply(BigDecimal.valueOf(100)).longValue());
    }

    private BookingResponse enrichBookingResponse(Booking booking) {
        BookingResponse response = bookingMapper.toResponse(booking);

        // Enrich with customer name
        if (booking.getCustomer() != null) {
            response = new BookingResponse(
                    response.id(),
                    response.customerId(),
                    booking.getCustomer().getFullName(),
                    response.serviceId(),
                    response.serviceTitle(),
                    response.vendorId(),
                    response.vendorName(),
                    response.startTime(),
                    response.endTime(),
                    response.status(),
                    response.quantity(),
                    response.totalPrice(),
                    response.currency(),
                    response.notes(),
                    response.createdAt(),
                    response.updatedAt()
            );
        }

        // Enrich with service title and vendor name
        if (booking.getService() != null) {
            String vendorName = booking.getService().getVendor() != null
                    ? booking.getService().getVendor().getBusinessName()
                    : null;

            response = new BookingResponse(
                    response.id(),
                    response.customerId(),
                    response.customerName(),
                    response.serviceId(),
                    booking.getService().getName(),
                    response.vendorId(),
                    vendorName,
                    response.startTime(),
                    response.endTime(),
                    response.status(),
                    response.quantity(),
                    response.totalPrice(),
                    response.currency(),
                    response.notes(),
                    response.createdAt(),
                    response.updatedAt()
            );
        }

        return response;
    }
}