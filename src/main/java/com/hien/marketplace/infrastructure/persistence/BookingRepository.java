package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Kiểm tra double-booking: có booking nào trùng slot chưa?
    boolean existsByServiceIdAndBookingDateAndTimeSlotStartTime(
        Long serviceId, LocalDate bookingDate, LocalTime startTime
    );

    List<Booking> findByVendorIdAndBookingDate(Long vendorId, LocalDate bookingDate);

    List<Booking> findByCustomerId(Long customerId);

    /**
     * Find bookings for customer with eager loading of relationships.
     *
     * WHY @EntityGraph: Prevents N+1 query problem.
     * Without EntityGraph: 1 query for bookings + N queries for service/customer/vendor.
     * With EntityGraph: 1 query with JOINs to fetch all relationships.
     *
     * The "booking-with-details" graph is defined in Booking entity via @NamedEntityGraph.
     */
    @EntityGraph(value = "booking-with-details", type = EntityGraph.EntityGraphType.LOAD)
    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);

    List<Booking> findByVendorId(Long vendorId);

    @Query("select b.status as status, count(b) as count from Booking b where b.vendor.id = :vendorId group by b.status")
    List<BookingStatusCount> countBookingsByStatusForVendor(Long vendorId);

    @Query("select count(distinct b.customer.id) from Booking b where b.vendor.id = :vendorId")
    long countDistinctCustomersByVendorId(Long vendorId);

    /**
     * Find bookings for vendor with eager loading of relationships.
     *
     * WHY @EntityGraph: Same N+1 prevention as findByCustomerId.
     */
    @EntityGraph(value = "booking-with-details", type = EntityGraph.EntityGraphType.LOAD)
    Page<Booking> findByVendorId(Long vendorId, Pageable pageable);

    List<Booking> findByServiceIdAndBookingDate(Long serviceId, LocalDate bookingDate);

    List<Booking> findByStatus(BookingStatus status);

    /**
     * Find bookings for a service on a specific date that are not cancelled.
     * Used for time slot overlap checking.
     *
     * WHY: We only check non-cancelled bookings because cancelled slots are free.
     *
     * @param serviceId the service ID
     * @param bookingDate the booking date
     * @return list of non-cancelled bookings for the service on that date
     */
    List<Booking> findByServiceIdAndBookingDateAndStatusNot(
        Long serviceId, LocalDate bookingDate, BookingStatus status
    );

    /**
     * Find booking by ID with a pessimistic write lock.
     *
     * Used during payment creation (PaymentTransactionService) to prevent concurrent
     * modifications. The lock is held until the transaction commits — combined with the
     * DB unique constraint on payments.booking_id, this catches duplicate-payment races.
     * Replaces the old OrderRepository.findByIdForUpdate after the Order→Booking merge.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(Long bookingId);

    interface BookingStatusCount {
        BookingStatus getStatus();
        Long getCount();
    }
}
