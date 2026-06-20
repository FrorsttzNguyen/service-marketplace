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
 * Unit tests cho Refund entity.
 *
 * WHY test domain entity:
 * - Entity methods chứa business logic quan trọng
 * - State machine transitions phải được test
 * - Entity là heart của domain model
 *
 * WHAT to test:
 * - Constructor tạo refund đúng status
 * - markAsProcessing() validates transition
 * - markAsSucceeded() validates transition
 * - markAsFailed() validates transition
 */
class RefundTest {

    private Refund refund;
    private Payment payment;

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
        Booking booking = new Booking(service, customer, vendor, LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(11, 0),
                Money.of(10000), Money.of(1000));

        payment = new Payment(booking, Money.of(11000));
        refund = new Refund(payment, Money.of(5000), "Customer request");
    }

    // === Constructor tests ===

    @Test
    void constructorShouldSetInitialStatus() {
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void constructorShouldSetAmount() {
        assertThat(refund.getAmount()).isEqualTo(Money.of(5000));
    }

    @Test
    void constructorShouldSetPayment() {
        assertThat(refund.getPayment()).isEqualTo(payment);
    }

    @Test
    void constructorShouldSetReason() {
        assertThat(refund.getReason()).isEqualTo("Customer request");
    }

    @Test
    void constructorShouldNotSetStripeRefundId() {
        assertThat(refund.getStripeRefundId()).isNull();
    }

    @Test
    void constructorShouldSetCreatedAt() {
        assertThat(refund.getCreatedAt()).isNotNull();
    }

    @Test
    void constructorWithNullReasonShouldWork() {
        Refund refundNoReason = new Refund(payment, Money.of(5000), null);
        assertThat(refundNoReason.getReason()).isNull();
    }

    // === State transition tests ===

    @Nested
    @DisplayName("markAsProcessing")
    class MarkAsProcessing {

        @Test
        void shouldSucceedFromPending() {
            assertThatCode(() -> refund.markAsProcessing())
                    .doesNotThrowAnyException();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        }

        @Test
        void shouldFailFromSucceeded() {
            refund.markAsProcessing();
            refund.markAsSucceeded();

            assertThatThrownBy(() -> refund.markAsProcessing())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition refund from SUCCEEDED to PROCESSING");
        }

        @Test
        void shouldFailFromProcessing() {
            refund.markAsProcessing();

            assertThatThrownBy(() -> refund.markAsProcessing())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition refund from PROCESSING to PROCESSING");
        }

        @Test
        void shouldFailFromFailed() {
            refund.markAsProcessing();
            refund.markAsFailed();

            assertThatThrownBy(() -> refund.markAsProcessing())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition refund from FAILED to PROCESSING");
        }
    }

    @Nested
    @DisplayName("markAsSucceeded")
    class MarkAsSucceeded {

        @Test
        void shouldSucceedFromProcessing() {
            refund.markAsProcessing();

            assertThatCode(() -> refund.markAsSucceeded())
                    .doesNotThrowAnyException();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        }

        @Test
        void shouldSucceedFromPendingForSyncRefund() {
            // Synchronous refunds (Stripe returns result immediately)
            // Skip PROCESSING state
            assertThatCode(() -> refund.markAsSucceeded())
                    .doesNotThrowAnyException();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        }

        @Test
        void shouldFailFromFailed() {
            refund.markAsProcessing();
            refund.markAsFailed();

            assertThatThrownBy(() -> refund.markAsSucceeded())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition refund from FAILED to SUCCEEDED");
        }
    }

    @Nested
    @DisplayName("markAsFailed")
    class MarkAsFailed {

        @Test
        void shouldSucceedFromProcessing() {
            refund.markAsProcessing();

            assertThatCode(() -> refund.markAsFailed())
                    .doesNotThrowAnyException();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        }

        @Test
        void shouldFailFromPending() {
            assertThatThrownBy(() -> refund.markAsFailed())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition refund from PENDING to FAILED");
        }

        @Test
        void shouldFailFromSucceeded() {
            refund.markAsProcessing();
            refund.markAsSucceeded();

            assertThatThrownBy(() -> refund.markAsFailed())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition refund from SUCCEEDED to FAILED");
        }
    }

    // === Lifecycle tests ===

    @Nested
    @DisplayName("Refund lifecycle")
    class RefundLifecycle {

        @Test
        void happyPathPendingToSucceeded() {
            // PENDING → PROCESSING → SUCCEEDED (async path)
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);

            refund.markAsProcessing();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);

            refund.markAsSucceeded();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        }

        @Test
        void syncRefundPath() {
            // PENDING → SUCCEEDED (synchronous refund - Stripe returns immediately)
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);

            refund.markAsSucceeded();
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        }

        @Test
        void failurePath() {
            // PENDING → PROCESSING → FAILED
            refund.markAsProcessing();
            refund.markAsFailed();

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        }

        @Test
        void terminalStateCannotChange() {
            // SUCCEEDED is terminal
            refund.markAsSucceeded();

            // Cannot transition from SUCCEEDED
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
            assertThatThrownBy(() -> refund.markAsProcessing())
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> refund.markAsFailed())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // === Setter tests ===

    @Test
    void setStripeRefundId() {
        refund.setStripeRefundId("re_test123");

        assertThat(refund.getStripeRefundId()).isEqualTo("re_test123");
    }

    // === Edge cases ===

    @Nested
    @DisplayName("Partial refund scenarios")
    class PartialRefundScenarios {

        @Test
        void partialRefundAmountLessThanPayment() {
            // Payment is 11000, refund 5000
            assertThat(refund.getAmount()).isEqualTo(Money.of(5000));
            assertThat(refund.getAmount().getAmountCents()).isLessThan(payment.getAmount().getAmountCents());
        }

        @Test
        void fullRefundAmountEqualsPayment() {
            Refund fullRefund = new Refund(payment, payment.getAmount(), "Full refund");

            assertThat(fullRefund.getAmount()).isEqualTo(payment.getAmount());
        }
    }
}