package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Kiểm tra double-booking: có booking nào trùng slot chưa?
    boolean existsByServiceIdAndBookingDateAndStartTime(
        Long serviceId, LocalDate bookingDate, java.time.LocalTime startTime
    );

    List<Booking> findByVendorIdAndBookingDate(Long vendorId, LocalDate bookingDate);

    List<Booking> findByCustomerId(Long customerId);

    List<Booking> findByServiceIdAndBookingDate(Long serviceId, LocalDate bookingDate);

    List<Booking> findByStatus(BookingStatus status);
}
