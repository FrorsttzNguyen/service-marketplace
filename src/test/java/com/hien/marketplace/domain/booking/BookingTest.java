package com.hien.marketplace.domain.booking;

import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.provider.Provider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho Booking entity state machine.
 * BookingStatusTest kiểm tra enum rules; class này kiểm tra entity dùng current status thật.
 *
 * New state machine (after Order→Booking merge):
 *   PENDING → CONFIRMED → PAID → IN_PROGRESS → COMPLETED
 * A booking must be PAID before it can move to IN_PROGRESS.
 */
class BookingTest {

    @Test
    void shouldCreatePendingBookingWithTimeSlotAndMoney() {
        Booking booking = newBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(booking.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(booking.getTimeSlot().toMinutes()).isEqualTo(60);
        // getTotal() = subtotal + commission = 5000 + 500 = 5500
        assertThat(booking.getTotal()).isEqualTo(Money.of(5500));
        assertThat(booking.getSubtotal()).isEqualTo(Money.of(5000));
        assertThat(booking.getCommission()).isEqualTo(Money.of(500));
        assertThat(booking.getServiceAddress()).isEqualTo(serviceAddress());
    }

    @Test
    void shouldConfirmPendingBookingAndCreateHistory() {
        Booking booking = newBooking();
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);

        booking.confirm(providerUser);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getStatusHistory()).hasSize(1);
        assertThat(booking.getStatusHistory().get(0).getFromStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getStatusHistory().get(0).getToStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void shouldCompleteValidLifecycle() {
        Booking booking = newBooking();
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);

        // New lifecycle: PENDING → CONFIRMED → PAID → IN_PROGRESS → COMPLETED
        booking.confirm(providerUser);
        booking.markAsPaid(null);  // payment webhook step (changedBy null = system)
        booking.start(providerUser);
        booking.complete(providerUser);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getStatusHistory()).extracting(BookingStatusHistory::getToStatus)
                .containsExactly(
                        BookingStatus.CONFIRMED,
                        BookingStatus.PAID,
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
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);

        booking.confirm(providerUser);
        booking.cancel(providerUser, "Provider unavailable");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatusHistory()).extracting(BookingStatusHistory::getToStatus)
                .containsExactly(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
    }

    @Test
    void shouldRejectStartingPendingBooking() {
        Booking booking = newBooking();

        assertThatThrownBy(() -> booking.start(booking.getProvider().getUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from PENDING to IN_PROGRESS");
    }

    @Test
    void shouldRejectStartingConfirmedBookingWithoutPayment() {
        // CONFIRMED → IN_PROGRESS is no longer a valid transition; must go through PAID first.
        Booking booking = newBooking();
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);
        booking.confirm(providerUser);

        assertThatThrownBy(() -> booking.start(providerUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from CONFIRMED to IN_PROGRESS");
    }

    @Test
    void shouldRejectCompletingConfirmedBooking() {
        Booking booking = newBooking();
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);
        booking.confirm(providerUser);

        assertThatThrownBy(() -> booking.complete(providerUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from CONFIRMED to COMPLETED");
    }

    @Test
    void shouldRejectConfirmingCancelledBooking() {
        Booking booking = newBooking();
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);
        booking.cancel(booking.getCustomer(), "No longer needed");

        assertThatThrownBy(() -> booking.confirm(providerUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from CANCELLED to CONFIRMED");
    }

    @Test
    void shouldRejectCancellingCompletedBooking() {
        Booking booking = newBooking();
        User providerUser = newUser("provider-owner@email.com", UserRole.VENDOR);
        booking.confirm(providerUser);
        booking.markAsPaid(null);
        booking.start(providerUser);
        booking.complete(providerUser);

        assertThatThrownBy(() -> booking.cancel(booking.getCustomer(), "Too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition booking from COMPLETED to CANCELLED");
    }

    @Test
    void shouldRejectInvalidTimeSlotInConstructor() {
        ServiceEntity service = newService();
        User customer = newUser("customer@email.com", UserRole.CUSTOMER);
        Provider provider = service.getProvider();

        assertThatThrownBy(() -> new Booking(
                service,
                customer,
                provider,
                LocalDate.of(2026, 6, 11),
                LocalTime.of(10, 0),
                LocalTime.of(9, 0),
                Money.of(5000),
                Money.of(500),
                serviceAddress()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Start time must be before end time");
    }

    private Booking newBooking() {
        ServiceEntity service = newService();
        User customer = newUser("customer@email.com", UserRole.CUSTOMER);
        return new Booking(
                service,
                customer,
                service.getProvider(),
                LocalDate.of(2026, 6, 11),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                Money.of(5000),
                Money.of(500),
                serviceAddress()
        );
    }

    private Address serviceAddress() {
        return new Address("123 Service Street", "Ho Chi Minh City", "70000");
    }

    private ServiceEntity newService() {
        User providerUser = newUser("provider@email.com", UserRole.VENDOR);
        Provider provider = new Provider(providerUser, "Hien Spa");
        return new ServiceEntity(provider, "Massage", Money.of(5000), PricingType.FIXED, 60);
    }

    private User newUser(String email, UserRole role) {
        return new User(email, "hashed_password", "Test User", role);
    }
}
