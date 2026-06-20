package com.hien.marketplace.application.service;

import com.hien.marketplace.application.event.BookingCancelledEvent;
import com.hien.marketplace.application.event.BookingConfirmedEvent;
import com.hien.marketplace.application.exception.BookingConflictException;
import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.BookingMapper;
import com.hien.marketplace.config.CommissionProperties;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.common.TimeSlot;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.UserRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.request.BookingCreateRequest;
import com.hien.marketplace.interfaces.dto.response.BookingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CommissionProperties commissionProperties;

    // Maximum retries for optimistic locking conflicts
    private static final int MAX_RETRIES = 3;
    // Initial delay in milliseconds for retry
    private static final long INITIAL_RETRY_DELAY_MS = 100;

    /**
     * Create new booking.
     *
     * Flow:
     * 1. Validate service exists and is active
     * 2. Validate customer exists
     * 3. Check time slot availability (Phase 3 - conflict detection)
     * 4. Calculate total price based on PricingType
     * 5. Create Booking entity
     * 6. Save and return response
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

        // Step 3: Check time slot availability
        LocalDate bookingDate = request.startTime().toLocalDate();
        LocalTime startTime = request.startTime().toLocalTime();
        LocalTime endTime = request.endTime().toLocalTime();

        checkTimeSlotAvailability(service.getId(), bookingDate, startTime, endTime);

        // Step 4: Calculate money breakdown.
        // subtotal = service price × quantity; commission = subtotal × rate (computed in cents
        // via Money to avoid float). After the Order→Booking merge, the booking carries the full
        // breakdown from the start (commission used to be computed later at order creation).
        Money subtotal = calculateTotalPrice(service, request.quantity());
        Money commission = subtotal.multiply(commissionProperties.getRate());
        // Service address belongs to the Booking because each visit can happen at a
        // different customer location even when the customer books the same service again.
        Address serviceAddress = new Address(request.street(), request.city(), request.zipCode());

        // Step 5: Create Booking
        Booking booking = new Booking(
                service,
                customer,
                service.getVendor(),
                bookingDate,
                startTime,
                endTime,
                subtotal,
                commission,
                serviceAddress
        );

        // Set notes if provided
        if (request.notes() != null) {
            booking.setNotes(request.notes());
        }

        // Step 6: Save
        booking = bookingRepository.save(booking);

        return enrichBookingResponse(booking);
    }

    /**
     * Check if time slot is available for booking.
     *
     * WHY: Prevent double-booking - same service can't have overlapping bookings.
     *
     * Algorithm:
     * 1. Get all non-cancelled bookings for this service on this date
     * 2. Check if requested time slot overlaps with any existing booking
     * 3. Throw BookingConflictException if overlap found
     *
     * @param serviceId the service ID
     * @param bookingDate the booking date
     * @param startTime requested start time
     * @param endTime requested end time
     * @throws BookingConflictException if time slot conflicts with existing booking
     */
    private void checkTimeSlotAvailability(Long serviceId, LocalDate bookingDate,
                                            LocalTime startTime, LocalTime endTime) {
        // Get all non-cancelled bookings for this service on this date
        List<Booking> existingBookings = bookingRepository
                .findByServiceIdAndBookingDateAndStatusNot(serviceId, bookingDate, BookingStatus.CANCELLED);

        // Create time slot for requested booking
        TimeSlot requestedSlot = new TimeSlot(startTime, endTime);

        // Check overlap with each existing booking
        for (Booking existing : existingBookings) {
            TimeSlot existingSlot = new TimeSlot(existing.getStartTime(), existing.getEndTime());

            if (requestedSlot.overlaps(existingSlot)) {
                throw new BookingConflictException(
                        serviceId,
                        bookingDate.atTime(startTime),
                        String.format("Time slot %s - %s is already booked for service %d on %s",
                                startTime, endTime, serviceId, bookingDate)
                );
            }
        }
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
     *
     * WHY: Vendor sees all bookings for their services.
     * Uses userId (from JWT) and looks up Vendor profile.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getVendorBookings(Long userId, Pageable pageable) {
        // Lookup vendor by userId
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Vendor profile",
                        "Vendor profile not found. Please complete vendor registration."
                ));

        return bookingRepository.findByVendorId(vendor.getId(), pageable)
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
        String reason = "Cancelled by customer";
        booking.cancel(customer, reason);

        booking = bookingRepository.save(booking);

        // Publish domain event for notifications
        eventPublisher.publishEvent(BookingCancelledEvent.from(booking, reason));

        return enrichBookingResponse(booking);
    }

    /**
     * Confirm booking (Vendor action).
     *
     * WHY: Vendor confirms a pending booking, changing status to CONFIRMED.
     * Uses optimistic locking with retry to handle concurrent updates.
     *
     * Optimistic Locking Scenario:
     * 1. Vendor A and Vendor B both try to confirm same booking simultaneously
     * 2. Both read booking with version=1
     * 3. Vendor A saves first → version becomes 2
     * 4. Vendor B tries to save → fails because version mismatch (expected 1, found 2)
     * 5. Retry: Vendor B re-reads booking with version=2, retries confirm
     *
     * @param userId the vendor's user ID (from JWT)
     * @param bookingId the booking to confirm
     * @return updated booking response
     * @throws BusinessRuleViolationException if booking doesn't belong to vendor
     */
    @Transactional
    public BookingResponse confirmBooking(Long userId, Long bookingId) {
        // Get vendor from userId
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Vendor profile",
                        "Vendor profile not found"
                ));

        // Retry loop for optimistic locking
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                Booking booking = bookingRepository.findById(bookingId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

                // Authorization: only vendor who owns the service can confirm
                if (!booking.getVendor().getId().equals(vendor.getId())) {
                    throw new BusinessRuleViolationException(
                            "Booking ownership",
                            "You can only confirm bookings for your own services"
                    );
                }

                // Domain method handles state machine validation
                User vendorUser = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                booking.confirm(vendorUser);

                booking = bookingRepository.save(booking);

                // Publish domain event for notifications
                eventPublisher.publishEvent(BookingConfirmedEvent.from(booking));

                return enrichBookingResponse(booking);

            } catch (ObjectOptimisticLockingFailureException e) {
                attempts++;
                log.warn("Optimistic lock conflict on booking {} (attempt {}/{}), retrying...",
                        bookingId, attempts, MAX_RETRIES);

                if (attempts >= MAX_RETRIES) {
                    log.error("Max retries ({}) reached for booking {}", MAX_RETRIES, bookingId);
                    throw new BusinessRuleViolationException(
                            "Concurrent modification",
                            "This booking was modified by another user. Please refresh and try again."
                    );
                }

                // Exponential backoff: 100ms, 200ms, 400ms...
                try {
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << (attempts - 1));
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessRuleViolationException(
                            "Operation interrupted",
                            "Please try again"
                    );
                }
                // Transaction will retry - Spring @Transactional handles this
            }
        }

        // Should never reach here, but compiler needs it
        throw new BusinessRuleViolationException(
                "Unexpected error",
                "Please try again"
        );
    }

    /**
     * Start service (Vendor action).
     *
     * WHY: Vendor marks a confirmed booking as IN_PROGRESS when service starts.
     */
    @Transactional
    public BookingResponse startService(Long userId, Long bookingId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Vendor profile",
                        "Vendor profile not found"
                ));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getVendor().getId().equals(vendor.getId())) {
            throw new BusinessRuleViolationException(
                    "Booking ownership",
                    "You can only start services for your own bookings"
            );
        }

        User vendorUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        booking.start(vendorUser);

        booking = bookingRepository.save(booking);
        return enrichBookingResponse(booking);
    }

    /**
     * Complete service (Vendor action).
     *
     * WHY: Vendor marks an IN_PROGRESS booking as COMPLETED after service is done.
     */
    @Transactional
    public BookingResponse completeService(Long userId, Long bookingId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Vendor profile",
                        "Vendor profile not found"
                ));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getVendor().getId().equals(vendor.getId())) {
            throw new BusinessRuleViolationException(
                    "Booking ownership",
                    "You can only complete services for your own bookings"
            );
        }

        User vendorUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        booking.complete(vendorUser);

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
                    response.serviceStreet(),
                    response.serviceCity(),
                    response.serviceZipCode(),
                    response.startTime(),
                    response.endTime(),
                    response.status(),
                    response.quantity(),
                    response.totalPrice(),
                    response.commission(),
                    response.total(),
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
                    response.serviceStreet(),
                    response.serviceCity(),
                    response.serviceZipCode(),
                    response.startTime(),
                    response.endTime(),
                    response.status(),
                    response.quantity(),
                    response.totalPrice(),
                    response.commission(),
                    response.total(),
                    response.currency(),
                    response.notes(),
                    response.createdAt(),
                    response.updatedAt()
            );
        }

        return response;
    }
}
