package com.hien.marketplace.domain.booking;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho Booking entity state machine.
 * BookingStatusTest kiểm tra enum rules; class này kiểm tra entity dùng current status thật.
 */
class BookingTest {

    @Test
    void shouldCreatePendingBookingWithTimeSlotAndMoney() {
        Booking booking = newBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(booking.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(booking.getTimeSlot().toMinutes()).isEqualTo(60);
        assertThat(booking.getTotalPrice()).isEqualTo(Money.of(5000));
    }

    @Test
    void shouldConfirmPendingBookingAndCreateHistory() {
        Booking booking = newBooking();
        User vendorUser = newUser("vendor-owner@email.com", UserRole.VENDOR);

        booking.confirm(vendorUser);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getStatusHistory()).hasSize(1);
        assertThat(booking.getStatusHistory().get(0).getFromStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getStatusHistory().get(0).getToStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void shouldCompleteValidLifecycle() {
        Booking booking = newBooking();
        User vendorUser = newUser("vendor-owner@email.com", UserRole.VENDOR);

        booking.confirm(vendorUser);
        booking.start(vendorUser);
        booking.complete(vendorUser);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getStatusHistory()).extracting(BookingStatusHistory::getToStatus)
                .containsExactly(
                        BookingStatus.CONFIRMED,
                        BookingStatus.IN_PROGRESS,
                        BookingStatus.COMPLETED
                );
    }

    @Test
    void shouldCancelPendingBooking() {
        Booking booking = newBooking();
        User customer = booking.getCustomer();

        booking.cancel(customer, "Customer changed plan");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatusHistory()).hasSize(1);
        assertThat(booking.getStatusHistory().get(0).getReason()).isEqualTo("Customer changed plan");
    }

    @Test
    void shouldCancelConfirmedBooking() {
        Booking booking = newBooking();
        User vendorUser = newUser("vendor-owner@email.com", UserRole.VENDOR);

        booking.confirm(vendorUser);
        booking.cancel(vendorUser, "Vendor unavailable");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatusHistory()).extracting(BookingStatusHistory::getToStatus)
                .containsExactly(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
    }

    @Test
    void shouldRejectStartingPendingBooking() {
        Booking booking = newBooking();

        assertThatThrownBy(() -> booking.start(booking.getVendor().getUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from PENDING to IN_PROGRESS");
    }

    @Test
    void shouldRejectCompletingConfirmedBooking() {
        Booking booking = newBooking();
        User vendorUser = newUser("vendor-owner@email.com", UserRole.VENDOR);
        booking.confirm(vendorUser);

        assertThatThrownBy(() -> booking.complete(vendorUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from CONFIRMED to COMPLETED");
    }

    @Test
    void shouldRejectConfirmingCancelledBooking() {
        Booking booking = newBooking();
        User vendorUser = newUser("vendor-owner@email.com", UserRole.VENDOR);
        booking.cancel(booking.getCustomer(), "No longer needed");

        assertThatThrownBy(() -> booking.confirm(vendorUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from CANCELLED to CONFIRMED");
    }

    @Test
    void shouldRejectCancellingCompletedBooking() {
        Booking booking = newBooking();
        User vendorUser = newUser("vendor-owner@email.com", UserRole.VENDOR);
        booking.confirm(vendorUser);
        booking.start(vendorUser);
        booking.complete(vendorUser);

        assertThatThrownBy(() -> booking.cancel(booking.getCustomer(), "Too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from COMPLETED to CANCELLED");
    }

    @Test
    void shouldRejectInvalidTimeSlotInConstructor() {
        ServiceEntity service = newService();
        User customer = newUser("customer@email.com", UserRole.CUSTOMER);
        Vendor vendor = service.getVendor();

        assertThatThrownBy(() -> new Booking(
                service,
                customer,
                vendor,
                LocalDate.of(2026, 6, 11),
                LocalTime.of(10, 0),
                LocalTime.of(9, 0),
                Money.of(5000)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Start time must be before end time");
    }

    private Booking newBooking() {
        ServiceEntity service = newService();
        User customer = newUser("customer@email.com", UserRole.CUSTOMER);
        return new Booking(
                service,
                customer,
                service.getVendor(),
                LocalDate.of(2026, 6, 11),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                Money.of(5000)
        );
    }

    private ServiceEntity newService() {
        User vendorUser = newUser("vendor@email.com", UserRole.VENDOR);
        Vendor vendor = new Vendor(vendorUser, "Hien Spa");
        return new ServiceEntity(vendor, "Massage", Money.of(5000), PricingType.FIXED, 60);
    }

    private User newUser(String email, UserRole role) {
        return new User(email, "hashed_password", "Test User", role);
    }
}
