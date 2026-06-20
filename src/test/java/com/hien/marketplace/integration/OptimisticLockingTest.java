package com.hien.marketplace.integration;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for optimistic locking behavior in Booking entity.
 *
 * WHY: Concurrent updates to the same booking must be handled gracefully.
 * @Version field in Booking enables JPA optimistic locking.
 *
 * Note: Full concurrent update testing requires integration tests with
 * actual concurrent threads. These tests verify version increment behavior.
 */
@DataJpaTest
@ActiveProfiles("test")
class OptimisticLockingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookingRepository bookingRepository;

    private Booking testBooking;
    private User vendorUser;
    private User customer;

    @BeforeEach
    void setUp() {
        // Create vendor
        vendorUser = new User("vendor-lock@test.com", "hashed", "Vendor", UserRole.VENDOR);
        vendorUser = entityManager.persistFlushFind(vendorUser);

        Vendor vendor = new Vendor(vendorUser, "Test Vendor");
        vendor = entityManager.persistFlushFind(vendor);

        // Create service
        ServiceEntity service = new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60);
        service.activate();
        service = entityManager.persistFlushFind(service);

        // Create customer
        customer = new User("customer-lock@test.com", "hashed", "Customer", UserRole.CUSTOMER);
        customer = entityManager.persistFlushFind(customer);

        // Create booking with its own service address for this appointment.
        testBooking = new Booking(
                service,
                customer,
                vendor,
                LocalDate.of(2026, 6, 15),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                Money.of(10000),
                Money.of(1000),
                new Address("123 Service Street", "Test City", "70000")
        );
        testBooking = entityManager.persistFlushFind(testBooking);
    }

    // ================================================================
    // Version Increment Tests
    // ================================================================

    @Nested
    @DisplayName("Version Increment")
    class VersionIncrementTests {

        @Test
        @DisplayName("Version should start at 0 for new booking")
        void versionShouldStartAtZero() {
            assertThat(testBooking.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Version should increment after update")
        void versionShouldIncrementAfterUpdate() {
            Long initialVersion = testBooking.getVersion();

            // Update booking
            testBooking.setNotes("Updated notes");
            testBooking = entityManager.persistFlushFind(testBooking);

            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 1);
        }

        @Test
        @DisplayName("Version should increment multiple times for multiple updates")
        void versionShouldIncrementMultipleTimes() {
            Long initialVersion = testBooking.getVersion();

            // First update
            testBooking.setNotes("First update");
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 1);

            // Second update
            testBooking.setNotes("Second update");
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 2);

            // Third update
            testBooking.confirm(vendorUser);
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 3);
        }
    }

    // ================================================================
    // Status Transition with Version Tests
    // ================================================================

    @Nested
    @DisplayName("Status Transitions with Version")
    class StatusTransitionTests {

        @Test
        @DisplayName("Confirm booking should increment version")
        void confirmShouldIncrementVersion() {
            Long initialVersion = testBooking.getVersion();

            testBooking.confirm(vendorUser);
            testBooking = entityManager.persistFlushFind(testBooking);

            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 1);
            assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Status transition chain should increment version each time")
        void statusChainShouldIncrementVersion() {
            Long initialVersion = testBooking.getVersion();

            // PENDING → CONFIRMED
            testBooking.confirm(vendorUser);
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 1);
            assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

            // CONFIRMED → PAID (new step: Stripe webhook, changedBy null = system)
            testBooking.markAsPaid(null);
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 2);
            assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.PAID);

            // PAID → IN_PROGRESS
            testBooking.start(vendorUser);
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 3);
            assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);

            // IN_PROGRESS → COMPLETED
            testBooking.complete(vendorUser);
            testBooking = entityManager.persistFlushFind(testBooking);
            assertThat(testBooking.getVersion()).isEqualTo(initialVersion + 4);
            assertThat(testBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        }
    }

    // ================================================================
    // Version in Response Tests
    // ================================================================

    @Nested
    @DisplayName("Version Persistence")
    class VersionPersistenceTests {

        @Test
        @DisplayName("Version should persist across find operations")
        void versionShouldPersistAcrossFinds() {
            Long bookingId = testBooking.getId();
            Long initialVersion = testBooking.getVersion();

            // Find the same booking again
            Booking found = entityManager.find(Booking.class, bookingId);

            assertThat(found.getVersion()).isEqualTo(initialVersion);
        }

        @Test
        @DisplayName("Different booking instances should have same version")
        void differentInstancesSameVersion() {
            Long bookingId = testBooking.getId();
            Long initialVersion = testBooking.getVersion();

            // Clear persistence context to force fresh read
            entityManager.clear();

            Booking fresh = entityManager.find(Booking.class, bookingId);

            assertThat(fresh.getVersion()).isEqualTo(initialVersion);
            assertThat(fresh.getId()).isEqualTo(testBooking.getId());
        }
    }
}
