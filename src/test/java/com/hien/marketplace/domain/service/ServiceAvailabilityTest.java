package com.hien.marketplace.domain.service;

import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho ServiceAvailability.
 * Entity này dùng TimeSlot để không duplicate rule startTime < endTime.
 */
class ServiceAvailabilityTest {

    @Test
    void shouldCreateAvailabilityWithTimeSlot() {
        ServiceEntity service = newService();

        ServiceAvailability availability = new ServiceAvailability(
                service,
                (short) 1,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );

        assertThat(availability.getDayOfWeek()).isEqualTo((short) 1);
        assertThat(availability.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(availability.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(availability.getTimeSlot().toMinutes()).isEqualTo(540);
    }

    @Test
    void shouldRejectInvalidDayOfWeek() {
        ServiceEntity service = newService();

        assertThatThrownBy(() -> new ServiceAvailability(
                service,
                (short) 7,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("dayOfWeek must be 0-6");
    }

    @Test
    void shouldRejectInvalidTimeSlot() {
        ServiceEntity service = newService();

        assertThatThrownBy(() -> new ServiceAvailability(
                service,
                (short) 1,
                LocalTime.of(18, 0),
                LocalTime.of(9, 0)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Start time must be before end time");
    }

    private ServiceEntity newService() {
        User vendorUser = new User("vendor@email.com", "hashed_password", "Vendor User", UserRole.VENDOR);
        Vendor vendor = new Vendor(vendorUser, "Hien Spa");
        return new ServiceEntity(vendor, "Massage", Money.of(5000), PricingType.FIXED, 60);
    }
}
