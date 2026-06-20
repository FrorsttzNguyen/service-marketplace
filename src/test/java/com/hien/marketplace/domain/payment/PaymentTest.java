package com.hien.marketplace.domain.payment;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho Payment entity.
 *
 * WHY test domain entity:
 * - Entity methods chứa business logic quan trọng
 * - State machine transitions phải được test
 * - Entity là heart của domain model
 *
 * WHAT to test:
 * - Constructor tạo payment đúng status
 * - markAsProcessing() validates transition
 * - markAsSucceeded() validates transition
 * - markAsFailed() validates transition
 * - resetForRetry() clears PaymentIntent ID
 */
class PaymentTest {

    private Payment payment;
    private Booking booking;

    @BeforeEach
    void setUp() {
        // Create customer
        User customer = new User("john@example.com", "hashedPassword", "John Doe", UserRole.CUSTOMER);

        // Create vendor with user
        User vendorUser = new User("vendor@example.com", "hashedPassword", "Vendor Name", UserRole.VENDOR);
        Vendor vendor = new Vendor(vendorUser, "Vendor Business");

        // Create service
        ServiceEntity service = new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60);

        // Create booking — new constructor: (service, customer, vendor, date, startTime, endTime, subtotal, commission)
        booking = new Booking(service, customer, vendor, LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(11, 0),
                Money.of(10000), Money.of(1000));

        payment = new Payment(booking, Money.of(11000));
    }

    // === Constructor tests ===

    @Test
    void constructorShouldSetInitialStatus() {
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void constructorShouldSetAmount() {
        assertThat(payment.getAmount()).isEqualTo(Money.of(11000));
    }

    @Test
    void constructorShouldSetBooking() {
        assertThat(payment.getBooking()).isEqualTo(booking);
    }

    @Test
    void constructorShouldNotSetStripePaymentIntentId() {
        assertThat(payment.getStripePaymentIntentId()).isNull();
    }

    @Test
    void constructorShouldNotSetPaymentMethod() {
        assertThat(payment.getPaymentMethod()).isNull();
    }

    // === State transition tests ===

    @Nested
    @DisplayName("markAsProcessing")
    class MarkAsProcessing {

        @Test
        void shouldSucceedFromPending() {
            assertThatCode(() -> payment.markAsProcessing())
                    .doesNotThrowAnyException();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        }

        @Test
        void shouldFailFromSucceeded() {
            payment.markAsProcessing();
            payment.markAsSucceeded();

            assertThatThrownBy(() -> payment.markAsProcessing())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from SUCCEEDED to PROCESSING");
        }

        @Test
        void shouldFailFromProcessing() {
            payment.markAsProcessing();

            assertThatThrownBy(() -> payment.markAsProcessing())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from PROCESSING to PROCESSING");
        }
    }

    @Nested
    @DisplayName("markAsSucceeded")
    class MarkAsSucceeded {

        @Test
        void shouldSucceedFromProcessing() {
            payment.markAsProcessing();

            assertThatCode(() -> payment.markAsSucceeded())
                    .doesNotThrowAnyException();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        void shouldFailFromPending() {
            assertThatThrownBy(() -> payment.markAsSucceeded())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from PENDING to SUCCEEDED");
        }

        @Test
        void shouldFailFromFailed() {
            payment.markAsProcessing();
            payment.markAsFailed();

            assertThatThrownBy(() -> payment.markAsSucceeded())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from FAILED to SUCCEEDED");
        }
    }

    @Nested
    @DisplayName("markAsFailed")
    class MarkAsFailed {

        @Test
        void shouldSucceedFromProcessing() {
            payment.markAsProcessing();

            assertThatCode(() -> payment.markAsFailed())
                    .doesNotThrowAnyException();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void shouldFailFromPending() {
            assertThatThrownBy(() -> payment.markAsFailed())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from PENDING to FAILED");
        }
    }

    @Nested
    @DisplayName("resetForRetry")
    class ResetForRetry {

        @Test
        void shouldSucceedFromFailed() {
            payment.markAsProcessing();
            payment.markAsFailed();
            payment.setStripePaymentIntentId("pi_old_intent");

            assertThatCode(() -> payment.resetForRetry())
                    .doesNotThrowAnyException();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void shouldClearStripePaymentIntentId() {
            payment.markAsProcessing();
            payment.markAsFailed();
            payment.setStripePaymentIntentId("pi_old_intent");

            payment.resetForRetry();

            assertThat(payment.getStripePaymentIntentId()).isNull();
        }

        @Test
        void shouldFailFromSucceeded() {
            payment.markAsProcessing();
            payment.markAsSucceeded();

            assertThatThrownBy(() -> payment.resetForRetry())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from SUCCEEDED to PENDING");
        }

        @Test
        void shouldFailFromPending() {
            assertThatThrownBy(() -> payment.resetForRetry())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from PENDING to PENDING");
        }
    }

    // === Lifecycle tests ===

    @Nested
    @DisplayName("Payment lifecycle")
    class PaymentLifecycle {

        @Test
        void happyPathPendingToSucceeded() {
            // PENDING → PROCESSING → SUCCEEDED
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            payment.markAsProcessing();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

            payment.markAsSucceeded();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        void failurePath() {
            // PENDING → PROCESSING → FAILED
            payment.markAsProcessing();
            payment.markAsFailed();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void retryPath() {
            // PENDING → PROCESSING → FAILED → PENDING → PROCESSING → SUCCEEDED
            payment.markAsProcessing();
            payment.markAsFailed();
            payment.resetForRetry();
            payment.markAsProcessing();
            payment.markAsSucceeded();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
    }

    // === Setter tests ===

    @Test
    void setStripePaymentIntentId() {
        payment.setStripePaymentIntentId("pi_test123");

        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test123");
    }

    @Test
    void setPaymentMethod() {
        payment.setPaymentMethod("card");

        assertThat(payment.getPaymentMethod()).isEqualTo("card");
    }
}
