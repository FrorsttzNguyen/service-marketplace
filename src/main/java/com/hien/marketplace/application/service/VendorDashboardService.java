package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.ReviewRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.response.VendorEarningsResponse;
import com.hien.marketplace.interfaces.dto.response.VendorStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only dashboard queries for the authenticated vendor.
 *
 * WHY a separate service: the controller should only translate HTTP/JWT concerns, while
 * business meanings such as "vendor earns subtotal, not commission" stay in application logic.
 */
@Service
@RequiredArgsConstructor
public class VendorDashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final VendorRepository vendorRepository;
    private final ServiceRepository serviceRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public VendorStatsResponse getStats(Long userId) {
        Vendor vendor = findVendor(userId);
        Long vendorId = vendor.getId();

        Map<BookingStatus, Long> countsByStatus = new EnumMap<>(BookingStatus.class);
        for (BookingRepository.BookingStatusCount row : bookingRepository.countBookingsByStatusForVendor(vendorId)) {
            countsByStatus.put(row.getStatus(), row.getCount());
        }

        Map<String, Integer> bookingsByStatus = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            bookingsByStatus.put(status.name(), toInteger(countsByStatus.getOrDefault(status, 0L)));
        }

        int totalBookings = bookingsByStatus.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        return new VendorStatsResponse(
                toInteger(serviceRepository.countByVendorId(vendorId)),
                toInteger(serviceRepository.countByVendorIdAndStatus(vendorId, ServiceStatus.ACTIVE)),
                totalBookings,
                bookingsByStatus.get(BookingStatus.PENDING.name()),
                bookingsByStatus.get(BookingStatus.CONFIRMED.name()),
                bookingsByStatus.get(BookingStatus.COMPLETED.name()),
                bookingsByStatus.get(BookingStatus.CANCELLED.name()),
                vendor.getRatingAvg(),
                toInteger(reviewRepository.countByVendorId(vendorId)),
                bookingsByStatus,
                toInteger(bookingRepository.countDistinctCustomersByVendorId(vendorId))
        );
    }

    @Transactional(readOnly = true)
    public VendorEarningsResponse getEarnings(Long userId) {
        Vendor vendor = findVendor(userId);

        long paidOutCents = 0L;
        long pendingPayoutCents = 0L;
        Map<String, Long> earningsByMonthCents = new LinkedHashMap<>();

        // After the Order→Booking merge, earnings are read straight off bookings.
        // Vendor earns the SUBTOTAL (the platform keeps the commission).
        //   PAID / IN_PROGRESS → money received but service not yet delivered → pending payout
        //   COMPLETED          → service delivered → paid out
        //   anything else (PENDING/CONFIRMED/CANCELLED/REFUNDED) → not counted
        for (Booking booking : bookingRepository.findByVendorId(vendor.getId())) {
            BookingStatus status = booking.getStatus();
            if (status != BookingStatus.PAID
                    && status != BookingStatus.IN_PROGRESS
                    && status != BookingStatus.COMPLETED) {
                continue;
            }

            long subtotalCents = booking.getSubtotal().getAmountCents();
            if (status == BookingStatus.COMPLETED) {
                paidOutCents += subtotalCents;
            } else {
                pendingPayoutCents += subtotalCents;
            }

            String month = YearMonth.from(booking.getCreatedAt()).format(MONTH_FORMATTER);
            earningsByMonthCents.merge(month, subtotalCents, Long::sum);
        }

        Map<String, BigDecimal> earningsByMonth = new LinkedHashMap<>();
        earningsByMonthCents.forEach((month, cents) -> earningsByMonth.put(month, toBigDecimal(cents)));

        return new VendorEarningsResponse(
                toBigDecimal(paidOutCents + pendingPayoutCents),
                toBigDecimal(pendingPayoutCents),
                toBigDecimal(paidOutCents),
                "USD",
                earningsByMonth
        );
    }

    private Vendor findVendor(Long userId) {
        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Vendor profile",
                        "Vendor profile not found. Please complete vendor registration."
                ));
    }

    private int toInteger(long value) {
        return Math.toIntExact(value);
    }

    private BigDecimal toBigDecimal(long cents) {
        return Money.of(cents).toBigDecimal();
    }
}
