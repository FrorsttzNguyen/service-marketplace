package com.hien.marketplace.integration;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.notification.Notification;
import com.hien.marketplace.domain.notification.NotificationType;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.domain.payment.RefundStatus;
import com.hien.marketplace.domain.review.Review;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.user.UserStatus;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.domain.vendor.VerificationStatus;
import com.hien.marketplace.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests cho repository layer.
 *
 * @DataJpaTest: Spring Boot tự động cấu hình:
 * - H2 in-memory database (từ application-test.yml, PostgreSQL compatibility mode)
 * - JPA + Hibernate + Spring Data repositories
 * - Mỗi test chạy trong transaction, rollback sau khi test xong → tests không ảnh hưởng nhau
 * - TestEntityManager: JPA EntityManager với convenience methods cho tests
 *
 * TODO: Migrate to TestContainers (real PostgreSQL) for production parity.
 * H2 compatibility mode doesn't catch all PostgreSQL-specific issues (e.g., jsonb types).
 *
 * Tại sao test repository?
 * - Verify entity mapping khớp với database schema (không lỗi JPA)
 * - Verify Spring Data derived query methods hoạt động đúng
 * - Verify embedded value objects (Money, TimeSlot, PhoneNumber) persist/read đúng
 */
@DataJpaTest
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // Counter để generate unique emails cho mỗi test — tránh unique constraint violation
    // khi persistFlushFind flush data xuống DB trong cùng transaction
    private static final AtomicInteger emailCounter = new AtomicInteger(0);

    private String uniqueEmail(String prefix) {
        return prefix + "-" + emailCounter.incrementAndGet() + "@test.com";
    }

    // ================================================================
    // Helper methods — tạo và persist test data
    // EntityManager.persistFlushFind() = persist + flush + clear + find
    // Đảm bảo entity thực sự được ghi xuống DB và đọc lại từ DB (không phải cache)
    // ================================================================

    private User persistUser(String email, UserRole role) {
        User user = new User(email, "hashed_password", "Test User", role);
        return entityManager.persistFlushFind(user);
    }

    private Vendor persistVendor(User user, String businessName) {
        Vendor vendor = new Vendor(user, businessName);
        return entityManager.persistFlushFind(vendor);
    }

    private ServiceEntity persistService(Vendor vendor, String name, Money basePrice) {
        ServiceEntity service = new ServiceEntity(vendor, name, basePrice, PricingType.FIXED, 60);
        service.activate();
        return entityManager.persistFlushFind(service);
    }

    private Booking persistBooking(ServiceEntity service, User customer, Vendor vendor,
                                     LocalDate date, LocalTime start, LocalTime end) {
        // New constructor takes (subtotal, commission) separately; commission = 0 for test simplicity
        Booking booking = new Booking(service, customer, vendor, date, start, end,
                Money.of(5000), Money.of(0), serviceAddress());
        return entityManager.persistFlushFind(booking);
    }

    private Address serviceAddress() {
        return new Address("123 Service Street", "Ho Chi Minh City", "70000");
    }

    // ================================================================
    // User Repository Tests
    // ================================================================

    @Nested
    class UserRepositoryTests {

        @Test
        void shouldPersistAndFindUserById() {
            User user = persistUser(uniqueEmail("user"), UserRole.CUSTOMER);

            Optional<User> found = userRepository.findById(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void shouldFindUserByEmail() {
            String email = uniqueEmail("find");
            persistUser(email, UserRole.VENDOR);

            Optional<User> found = userRepository.findByEmail(email);

            assertThat(found).isPresent();
            assertThat(found.get().getRole()).isEqualTo(UserRole.VENDOR);
        }

        @Test
        void shouldReturnEmptyWhenEmailNotFound() {
            Optional<User> found = userRepository.findByEmail("nonexistent@email.com");

            assertThat(found).isEmpty();
        }

        @Test
        void shouldCheckEmailExists() {
            String email = uniqueEmail("exists");
            persistUser(email, UserRole.CUSTOMER);

            assertThat(userRepository.existsByEmail(email)).isTrue();
            assertThat(userRepository.existsByEmail("nope@email.com")).isFalse();
        }

        @Test
        void shouldFindUsersByRole() {
            persistUser(uniqueEmail("c"), UserRole.CUSTOMER);
            persistUser(uniqueEmail("c"), UserRole.CUSTOMER);
            persistUser(uniqueEmail("v"), UserRole.VENDOR);

            List<User> customers = userRepository.findByRole(UserRole.CUSTOMER);

            assertThat(customers).hasSize(2);
            assertThat(customers).allMatch(u -> u.getRole() == UserRole.CUSTOMER);
        }
    }

    // ================================================================
    // Vendor Repository Tests
    // ================================================================

    @Nested
    class VendorRepositoryTests {

        @Test
        void shouldPersistVendorWithComposition() {
            User user = persistUser(uniqueEmail("vendor"), UserRole.VENDOR);
            Vendor vendor = persistVendor(user, "Test Business");

            assertThat(vendor.getId()).isNotNull();
            assertThat(vendor.getUser().getId()).isEqualTo(user.getId());
            assertThat(vendor.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
            assertThat(vendor.getRatingAvg()).isEqualByComparingTo("0.00");
        }

        @Test
        void shouldFindVendorByUserId() {
            User user = persistUser(uniqueEmail("vendor"), UserRole.VENDOR);
            Vendor vendor = persistVendor(user, "Test Business");

            Optional<Vendor> found = vendorRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getBusinessName()).isEqualTo("Test Business");
        }

        @Test
        void shouldCheckVendorExistsByUserId() {
            User user = persistUser(uniqueEmail("vendor"), UserRole.VENDOR);
            persistVendor(user, "Test Business");

            assertThat(vendorRepository.existsByUserId(user.getId())).isTrue();
            assertThat(vendorRepository.existsByUserId(999L)).isFalse();
        }

        @Test
        void shouldFindVendorsByVerificationStatus() {
            User u1 = persistUser(uniqueEmail("v1"), UserRole.VENDOR);
            User u2 = persistUser(uniqueEmail("v2"), UserRole.VENDOR);
            Vendor approved = persistVendor(u1, "Approved Co");
            approved.approve();
            entityManager.persistAndFlush(approved);
            persistVendor(u2, "Pending Co");

            List<Vendor> approvedVendors = vendorRepository.findByVerificationStatus(VerificationStatus.APPROVED);

            assertThat(approvedVendors).hasSize(1);
            assertThat(approvedVendors.get(0).getBusinessName()).isEqualTo("Approved Co");
        }
    }

    // ================================================================
    // Service Repository Tests
    // ================================================================

    @Nested
    class ServiceRepositoryTests {

        private Vendor vendor;

        @BeforeEach
        void setUp() {
            User user = persistUser(uniqueEmail("svc-vendor"), UserRole.VENDOR);
            vendor = persistVendor(user, "Test Vendor");
        }

        @Test
        void shouldPersistServiceWithEmbeddedMoney() {
            ServiceEntity service = persistService(vendor, "Test Service", Money.of(10000));

            assertThat(service.getId()).isNotNull();
            // Verify embedded Money persists correctly — 10000 cents should round-trip
            assertThat(service.getBasePrice()).isEqualTo(Money.of(10000));
            assertThat(service.getStatus()).isEqualTo(ServiceStatus.ACTIVE);
        }

        @Test
        void shouldFindServicesByVendorId() {
            persistService(vendor, "Service A", Money.of(5000));
            persistService(vendor, "Service B", Money.of(8000));

            List<ServiceEntity> services = serviceRepository.findByVendorId(vendor.getId());

            assertThat(services).hasSize(2);
        }

        @Test
        void shouldFindServicesByStatus() {
            persistService(vendor, "Active Service", Money.of(5000));
            ServiceEntity draft = new ServiceEntity(vendor, "Draft", Money.of(3000), PricingType.HOURLY, 60);
            entityManager.persistAndFlush(draft); // DRAFT by default

            List<ServiceEntity> activeServices = serviceRepository.findByStatus(ServiceStatus.ACTIVE);

            assertThat(activeServices).hasSize(1);
            assertThat(activeServices.get(0).getName()).isEqualTo("Active Service");
        }

        @Test
        void shouldCalculatePriceWithStrategy() {
            ServiceEntity hourlyService = new ServiceEntity(vendor, "Consultation",
                    Money.of(5000), PricingType.HOURLY, 120);
            entityManager.persistAndFlush(hourlyService);

            // HOURLY pricing: basePrice × (duration / 60) = 5000 × 2 = 10000
            Money price = hourlyService.calculatePrice();
            assertThat(price).isEqualTo(Money.of(10000));
        }
    }

    // ================================================================
    // Booking Repository Tests
    // ================================================================

    @Nested
    class BookingRepositoryTests {

        private ServiceEntity service;
        private User customer;
        private Vendor vendor;

        @BeforeEach
        void setUp() {
            User vendorUser = persistUser(uniqueEmail("bk-vendor"), UserRole.VENDOR);
            vendor = persistVendor(vendorUser, "Test Vendor");
            service = persistService(vendor, "Test Service", Money.of(5000));
            customer = persistUser(uniqueEmail("bk-customer"), UserRole.CUSTOMER);
        }

        @Test
        void shouldPersistBookingWithEmbeddedTimeSlotAndMoney() {
            Booking booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));

            assertThat(booking.getId()).isNotNull();
            assertThat(booking.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(booking.getEndTime()).isEqualTo(LocalTime.of(10, 0));
            // getTotal() = subtotal + commission = 5000 + 0 (no commission in test) = 5000
            assertThat(booking.getTotal()).isEqualTo(Money.of(5000));
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        }

        @Test
        void shouldPersistAndReadBookingServiceAddress() {
            Booking booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));

            assertThat(booking.getServiceAddress()).isEqualTo(serviceAddress());
            assertThat(booking.getServiceAddress().getStreet()).isEqualTo("123 Service Street");
            assertThat(booking.getServiceAddress().getCity()).isEqualTo("Ho Chi Minh City");
            assertThat(booking.getServiceAddress().getZipCode()).isEqualTo("70000");
        }

        @Test
        void shouldCheckDoubleBookingSlotExists() {
            LocalDate date = LocalDate.of(2026, 6, 15);
            LocalTime start = LocalTime.of(9, 0);
            LocalTime end = LocalTime.of(10, 0);
            persistBooking(service, customer, vendor, date, start, end);

            // Same service + same date + same start time → should report exists
            boolean exists = bookingRepository.existsByServiceIdAndBookingDateAndTimeSlotStartTime(
                    service.getId(), date, start);

            assertThat(exists).isTrue();
        }

        @Test
        void shouldReturnFalseWhenSlotIsFree() {
            LocalDate date = LocalDate.of(2026, 6, 15);

            boolean exists = bookingRepository.existsByServiceIdAndBookingDateAndTimeSlotStartTime(
                    service.getId(), date, LocalTime.of(9, 0));

            assertThat(exists).isFalse();
        }

        @Test
        void shouldFindBookingsByVendorAndDate() {
            LocalDate date = LocalDate.of(2026, 6, 15);
            persistBooking(service, customer, vendor, date,
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            persistBooking(service, customer, vendor, date,
                    LocalTime.of(11, 0), LocalTime.of(12, 0));

            List<Booking> bookings = bookingRepository.findByVendorIdAndBookingDate(vendor.getId(), date);

            assertThat(bookings).hasSize(2);
        }

        @Test
        void shouldFindBookingsByCustomer() {
            persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));

            List<Booking> bookings = bookingRepository.findByCustomerId(customer.getId());

            assertThat(bookings).hasSize(1);
            assertThat(bookings.get(0).getCustomer().getId()).isEqualTo(customer.getId());
        }

        @Test
        void shouldFindBookingsByStatus() {
            Booking booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));
            // Confirm the booking — change status from PENDING to CONFIRMED
            User vendorUser = entityManager.find(User.class, vendor.getUser().getId());
            booking.confirm(vendorUser);
            entityManager.persistAndFlush(booking);

            List<Booking> confirmed = bookingRepository.findByStatus(BookingStatus.CONFIRMED);
            List<Booking> pending = bookingRepository.findByStatus(BookingStatus.PENDING);

            assertThat(confirmed).hasSize(1);
            assertThat(pending).isEmpty();
        }

        @Test
        void shouldPersistStatusHistoryOnTransition() {
            Booking booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));
            User vendorUser = entityManager.find(User.class, vendor.getUser().getId());

            // New lifecycle: PENDING → CONFIRMED → PAID → IN_PROGRESS
            // markAsPaid(null) simulates the Stripe webhook (changedBy=null = system action)
            booking.confirm(vendorUser);
            booking.markAsPaid(null);
            booking.start(vendorUser);
            booking = entityManager.persistFlushFind(booking);

            // 3 status history entries: PENDING→CONFIRMED + CONFIRMED→PAID + PAID→IN_PROGRESS
            assertThat(booking.getStatusHistory()).hasSize(3);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        }
    }

    // ================================================================
    // Payment Repository Tests (after Order→Booking merge)
    // ================================================================

    @Nested
    class PaymentRepositoryTests {

        private Booking booking;
        private User customer;
        private Vendor vendor;

        @BeforeEach
        void setUp() {
            User vendorUser = persistUser(uniqueEmail("op-vendor"), UserRole.VENDOR);
            vendor = persistVendor(vendorUser, "Test Vendor");
            ServiceEntity service = persistService(vendor, "Test Service", Money.of(5000));
            customer = persistUser(uniqueEmail("op-customer"), UserRole.CUSTOMER);
            booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));
        }

        @Test
        void shouldPersistPaymentWithStripeId() {
            // Payment now references Booking directly (Order removed after merge)
            Payment payment = new Payment(booking, Money.of(5000));
            payment.setStripePaymentIntentId("pi_test_123");
            payment = entityManager.persistFlushFind(payment);

            assertThat(payment.getId()).isNotNull();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test_123");
            assertThat(payment.getBooking().getId()).isEqualTo(booking.getId());
        }

        @Test
        void shouldFindPaymentByStripeId() {
            Payment payment = new Payment(booking, Money.of(5000));
            payment.setStripePaymentIntentId("pi_test_unique");
            entityManager.persistAndFlush(payment);

            Optional<Payment> found = paymentRepository.findByStripePaymentIntentId("pi_test_unique");

            assertThat(found).isPresent();
            assertThat(found.get().getAmount()).isEqualTo(Money.of(5000));
        }

        @Test
        void shouldCheckPaymentExistsByStripeId() {
            Payment payment = new Payment(booking, Money.of(5000));
            payment.setStripePaymentIntentId("pi_test_exists");
            entityManager.persistAndFlush(payment);

            assertThat(paymentRepository.existsByStripePaymentIntentId("pi_test_exists")).isTrue();
            assertThat(paymentRepository.existsByStripePaymentIntentId("pi_test_nope")).isFalse();
        }

        @Test
        void shouldFindPaymentByBookingId() {
            Payment payment = new Payment(booking, Money.of(5000));
            entityManager.persistAndFlush(payment);

            Optional<Payment> found = paymentRepository.findByBookingId(booking.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getAmount()).isEqualTo(Money.of(5000));
        }

        @Test
        void shouldPersistRefundCascadeFromPayment() {
            Payment payment = new Payment(booking, Money.of(5000));
            entityManager.persistAndFlush(payment);

            // Refund linked to payment — CascadeType.ALL means saving payment saves refund too
            Refund refund = new Refund(payment, Money.of(2000), "Customer request");
            payment.getRefunds().add(refund);
            payment = entityManager.persistFlushFind(payment);

            assertThat(payment.getRefunds()).hasSize(1);
            assertThat(payment.getRefunds().get(0).getStatus()).isEqualTo(RefundStatus.PENDING);
            assertThat(payment.getRefunds().get(0).getAmount()).isEqualTo(Money.of(2000));
        }
    }

    // ================================================================
    // Category Repository Tests
    // ================================================================

    @Nested
    class CategoryRepositoryTests {

        @Test
        void shouldPersistCategoryWithSelfReferencing() {
            Category parent = new Category("Spa", "spa");
            parent = entityManager.persistFlushFind(parent);

            Category child = new Category("Facial", "facial", parent);
            child = entityManager.persistFlushFind(child);

            assertThat(child.getParent()).isNotNull();
            assertThat(child.getParent().getName()).isEqualTo("Spa");
            assertThat(parent.isTopLevel()).isTrue();
            assertThat(child.isTopLevel()).isFalse();
        }
    }

    // ================================================================
    // Review Repository Tests
    // ================================================================

    @Nested
    class ReviewRepositoryTests {

        @Test
        void shouldPersistReviewWithRatingValidation() {
            User vendorUser = persistUser(uniqueEmail("rv-vendor"), UserRole.VENDOR);
            Vendor vendor = persistVendor(vendorUser, "Test Vendor");
            ServiceEntity service = persistService(vendor, "Test Service", Money.of(5000));
            User customer = persistUser(uniqueEmail("rv-customer"), UserRole.CUSTOMER);
            Booking booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));

            Review review = new Review(booking, customer, vendor, service, 5, "Excellent!");
            review = entityManager.persistFlushFind(review);

            assertThat(review.getId()).isNotNull();
            assertThat(review.getRating()).isEqualTo(5);
            assertThat(review.getComment()).isEqualTo("Excellent!");
        }

        @Test
        void shouldRejectInvalidRating() {
            User vendorUser = persistUser(uniqueEmail("rv-vendor"), UserRole.VENDOR);
            Vendor vendor = persistVendor(vendorUser, "Test Vendor");
            ServiceEntity service = persistService(vendor, "Test Service", Money.of(5000));
            User customer = persistUser(uniqueEmail("rv-customer"), UserRole.CUSTOMER);
            Booking booking = persistBooking(service, customer, vendor,
                    LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), LocalTime.of(10, 0));

            assertThatThrownBy(() -> new Review(booking, customer, vendor, service, 0, "Bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rating must be between 1 and 5");

            assertThatThrownBy(() -> new Review(booking, customer, vendor, service, 6, "Too high"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rating must be between 1 and 5");
        }
    }

    // ================================================================
    // Notification Repository Tests
    // ================================================================

    @Nested
    class NotificationRepositoryTests {

        @Autowired
        private NotificationRepository notificationRepository;

        @Test
        void shouldPersistNotificationAndMarkAsRead() {
            User user = persistUser(uniqueEmail("ntf-user"), UserRole.CUSTOMER);

            Notification notification = new Notification(user, NotificationType.BOOKING_CONFIRMED,
                    "Booking Confirmed", "Your booking has been confirmed");
            notification = entityManager.persistFlushFind(notification);

            assertThat(notification.getId()).isNotNull();
            assertThat(notification.isRead()).isFalse();

            notification.markAsRead();
            entityManager.persistAndFlush(notification);

            Notification updated = entityManager.find(Notification.class, notification.getId());
            assertThat(updated.isRead()).isTrue();
        }
    }
}
