package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.domain.order.OrderStatus;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.OrderRepository;
import com.hien.marketplace.infrastructure.persistence.ReviewRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.response.VendorEarningsResponse;
import com.hien.marketplace.interfaces.dto.response.VendorStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VendorDashboardService — vendor analytics and earnings rules.
 *
 * WHY test with mocks: dashboard data comes from several repositories, but the important
 * behavior is the aggregation contract: missing statuses become zero and vendor earnings
 * use Order.subtotal only (commission is the platform's cut).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorDashboardServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderRepository orderRepository;

    private VendorDashboardService vendorDashboardService;

    private static final Long USER_ID = 1L;
    private static final Long VENDOR_ID = 30L;

    private User customer;
    private User vendorUser;
    private Vendor vendor;
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        vendorDashboardService = new VendorDashboardService(
                vendorRepository,
                serviceRepository,
                bookingRepository,
                reviewRepository,
                orderRepository
        );

        customer = new User("customer@example.com", "hashed", "Customer", UserRole.CUSTOMER);
        vendorUser = new User("vendor@example.com", "hashed", "Vendor", UserRole.VENDOR);
        vendor = spy(new Vendor(vendorUser, "Vendor Biz"));
        when(vendor.getId()).thenReturn(VENDOR_ID);
        vendor.updateRating(new BigDecimal("4.50"));

        ServiceEntity service = new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60);
        confirmedBooking = new Booking(service, customer, vendor, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), Money.of(10000));
        confirmedBooking.confirm(vendorUser);

        when(vendorRepository.findByUserId(USER_ID)).thenReturn(Optional.of(vendor));
    }

    @Test
    @DisplayName("stats: service counts, booking counts, missing statuses as zero, reviews/customers/rating")
    void shouldBuildStatsFromRepositoryCounts() {
        List<BookingRepository.BookingStatusCount> statusCounts = List.of(
                bookingStatusCount(BookingStatus.PENDING, 2L),
                bookingStatusCount(BookingStatus.CONFIRMED, 3L),
                bookingStatusCount(BookingStatus.COMPLETED, 5L),
                bookingStatusCount(BookingStatus.CANCELLED, 1L)
        );

        when(serviceRepository.countByVendorId(VENDOR_ID)).thenReturn(7L);
        when(serviceRepository.countByVendorIdAndStatus(VENDOR_ID, ServiceStatus.ACTIVE)).thenReturn(4L);
        when(bookingRepository.countBookingsByStatusForVendor(VENDOR_ID)).thenReturn(statusCounts);
        when(reviewRepository.countByVendorId(VENDOR_ID)).thenReturn(9L);
        when(bookingRepository.countDistinctCustomersByVendorId(VENDOR_ID)).thenReturn(6L);

        VendorStatsResponse response = vendorDashboardService.getStats(USER_ID);

        assertThat(response.totalServices()).isEqualTo(7);
        assertThat(response.activeServices()).isEqualTo(4);
        assertThat(response.totalBookings()).isEqualTo(11);
        assertThat(response.pendingBookings()).isEqualTo(2);
        assertThat(response.confirmedBookings()).isEqualTo(3);
        assertThat(response.completedBookings()).isEqualTo(5);
        assertThat(response.cancelledBookings()).isEqualTo(1);
        assertThat(response.averageRating()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(response.totalReviews()).isEqualTo(9);
        assertThat(response.totalCustomers()).isEqualTo(6);
        assertThat(response.bookingsByStatus()).containsExactly(
                org.assertj.core.api.Assertions.entry("PENDING", 2),
                org.assertj.core.api.Assertions.entry("CONFIRMED", 3),
                org.assertj.core.api.Assertions.entry("IN_PROGRESS", 0),
                org.assertj.core.api.Assertions.entry("COMPLETED", 5),
                org.assertj.core.api.Assertions.entry("CANCELLED", 1)
        );
    }

    @Test
    @DisplayName("earnings: PAID/FULFILLED subtotal only, split payouts, exclude unpaid/refunded/cancelled, group by yyyy-MM")
    void shouldCalculateEarningsFromPaidAndFulfilledSubtotalsOnly() {
        Order paidJanuary = order(OrderStatus.PAID, 10_000, 1_000, LocalDateTime.of(2026, 1, 15, 9, 0));
        Order fulfilledJanuary = order(OrderStatus.FULFILLED, 20_000, 2_000, LocalDateTime.of(2026, 1, 20, 9, 0));
        Order fulfilledFebruary = order(OrderStatus.FULFILLED, 5_555, 555, LocalDateTime.of(2026, 2, 1, 9, 0));
        Order created = order(OrderStatus.CREATED, 99_999, 9_999, LocalDateTime.of(2026, 1, 1, 9, 0));
        Order pending = order(OrderStatus.PENDING_PAYMENT, 88_888, 8_888, LocalDateTime.of(2026, 1, 2, 9, 0));
        Order cancelled = order(OrderStatus.CANCELLED, 77_777, 7_777, LocalDateTime.of(2026, 1, 3, 9, 0));
        Order refunded = order(OrderStatus.REFUNDED, 66_666, 6_666, LocalDateTime.of(2026, 1, 4, 9, 0));
        when(orderRepository.findByVendorId(VENDOR_ID)).thenReturn(List.of(
                paidJanuary,
                fulfilledJanuary,
                fulfilledFebruary,
                created,
                pending,
                cancelled,
                refunded
        ));

        VendorEarningsResponse response = vendorDashboardService.getEarnings(USER_ID);

        assertThat(response.pendingPayouts()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.paidOut()).isEqualByComparingTo(new BigDecimal("255.55"));
        assertThat(response.totalEarnings()).isEqualByComparingTo(new BigDecimal("355.55"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.earningsByMonth()).containsExactly(
                org.assertj.core.api.Assertions.entry("2026-01", new BigDecimal("300.00")),
                org.assertj.core.api.Assertions.entry("2026-02", new BigDecimal("55.55"))
        );
    }

    @Test
    @DisplayName("vendor not found → BusinessRuleViolationException")
    void shouldThrowWhenVendorProfileMissing() {
        when(vendorRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorDashboardService.getStats(USER_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Vendor profile not found. Please complete vendor registration.");
        assertThatThrownBy(() -> vendorDashboardService.getEarnings(USER_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Vendor profile not found. Please complete vendor registration.");
    }

    private BookingRepository.BookingStatusCount bookingStatusCount(BookingStatus status, Long count) {
        BookingRepository.BookingStatusCount row = mock(BookingRepository.BookingStatusCount.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getCount()).thenReturn(count);
        return row;
    }

    private Order order(OrderStatus status, long subtotalCents, long commissionCents, LocalDateTime createdAt) {
        Order order = new Order(customer, confirmedBooking, Money.of(subtotalCents), Money.of(commissionCents));
        if (status == OrderStatus.PENDING_PAYMENT) {
            order.markAsPendingPayment();
        } else if (status == OrderStatus.PAID) {
            order.markAsPendingPayment();
            order.markAsPaid();
        } else if (status == OrderStatus.FULFILLED) {
            order.markAsPendingPayment();
            order.markAsPaid();
            order.fulfill();
        } else if (status == OrderStatus.CANCELLED) {
            order.cancel();
        } else if (status == OrderStatus.REFUNDED) {
            order.markAsPendingPayment();
            order.markAsPaid();
            order.refund();
        }

        Order spied = spy(order);
        when(spied.getCreatedAt()).thenReturn(createdAt);
        return spied;
    }
}
