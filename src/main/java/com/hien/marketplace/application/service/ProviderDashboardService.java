package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.ReviewRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.ProviderRepository;
import com.hien.marketplace.interfaces.dto.response.ProviderEarningsResponse;
import com.hien.marketplace.interfaces.dto.response.ProviderStatsResponse;
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
 * Read-only dashboard queries for the authenticated provider.
 *
 * WHY a separate service: the controller should only translate HTTP/JWT concerns, while
 * business meanings such as "provider earns subtotal, not commission" stay in application logic.
 */
@Service
@RequiredArgsConstructor
public class ProviderDashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public ProviderStatsResponse getStats(Long userId) {
        Provider provider = findProvider(userId);
        Long providerId = provider.getId();

        Map<BookingStatus, Long> countsByStatus = new EnumMap<>(BookingStatus.class);
        for (BookingRepository.BookingStatusCount row : bookingRepository.countBookingsByStatusForProvider(providerId)) {
            countsByStatus.put(row.getStatus(), row.getCount());
        }

        Map<String, Integer> bookingsByStatus = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            bookingsByStatus.put(status.name(), toInteger(countsByStatus.getOrDefault(status, 0L)));
        }

        int totalBookings = bookingsByStatus.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        return new ProviderStatsResponse(
                toInteger(serviceRepository.countByProviderId(providerId)),
                toInteger(serviceRepository.countByProviderIdAndStatus(providerId, ServiceStatus.ACTIVE)),
                totalBookings,
                bookingsByStatus.get(BookingStatus.PENDING.name()),
                bookingsByStatus.get(BookingStatus.CONFIRMED.name()),
                bookingsByStatus.get(BookingStatus.COMPLETED.name()),
                bookingsByStatus.get(BookingStatus.CANCELLED.name()),
                provider.getRatingAvg(),
                toInteger(reviewRepository.countByProviderId(providerId)),
                bookingsByStatus,
                toInteger(bookingRepository.countDistinctCustomersByProviderId(providerId))
        );
    }

    @Transactional(readOnly = true)
    public ProviderEarningsResponse getEarnings(Long userId) {
        Provider provider = findProvider(userId);

        long paidOutCents = 0L;
        long pendingPayoutCents = 0L;
        Map<String, Long> earningsByMonthCents = new LinkedHashMap<>();

        // After the Order→Booking merge, earnings are read straight off bookings.
        // Provider earns the SUBTOTAL (the platform keeps the commission).
        //   PAID / IN_PROGRESS → money received but service not yet delivered → pending payout
        //   COMPLETED          → service delivered → paid out
        //   anything else (PENDING/CONFIRMED/CANCELLED/REFUNDED) → not counted
        for (Booking booking : bookingRepository.findByProviderId(provider.getId())) {
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

        return new ProviderEarningsResponse(
                toBigDecimal(paidOutCents + pendingPayoutCents),
                toBigDecimal(pendingPayoutCents),
                toBigDecimal(paidOutCents),
                "USD",
                earningsByMonth
        );
    }

    private Provider findProvider(Long userId) {
        return providerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Provider profile",
                        "Provider profile not found. Please complete provider registration."
                ));
    }

    private int toInteger(long value) {
        return Math.toIntExact(value);
    }

    private BigDecimal toBigDecimal(long cents) {
        return Money.of(cents).toBigDecimal();
    }
}
