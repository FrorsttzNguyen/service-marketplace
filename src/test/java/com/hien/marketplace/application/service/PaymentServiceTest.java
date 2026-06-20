package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.*;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import com.hien.marketplace.domain.payment.events.PaymentFailedEvent;
import com.hien.marketplace.domain.payment.events.PaymentSucceededEvent;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.PaymentRepository;
import com.hien.marketplace.infrastructure.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho PaymentService (after Order→Booking merge).
 *
 * WHY test service:
 * - Service contains complex business logic
 * - Authorization checks
 * - Stripe API integration
 * - Transaction boundaries
 *
 * After the merge: Payment references Booking directly (no Order).
 * createPayment(userId, bookingId, method) — takes bookingId, not orderId.
 * PaymentTransactionService.createPaymentWithBookingUpdate (was ...WithOrderUpdate).
 *
 * @MockitoSettings(strictness = Strictness.LENIENT) allows unused stubs
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @InjectMocks
    private PaymentService paymentService;

    private User customer;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        // Create customer
        customer = new User("john@example.com", "hashedPassword", "John Doe", UserRole.CUSTOMER);
        customer = spy(customer);
        when(customer.getId()).thenReturn(1L);

        // Create vendor
        User vendorUser = new User("vendor@example.com", "hashedPassword", "Vendor", UserRole.VENDOR);
        Vendor vendor = new Vendor(vendorUser, "Vendor Business");
        vendor = spy(vendor);
        when(vendor.getId()).thenReturn(1L);

        // Create service
        ServiceEntity service = new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60);

        // Create booking — new constructor takes subtotal + commission separately
        booking = new Booking(service, customer, vendor, LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(11, 0),
                Money.of(10000), Money.of(1000));
        booking = spy(booking);
        when(booking.getId()).thenReturn(1L);

        // Payment now references Booking directly (not Order)
        payment = new Payment(booking, Money.of(11000));
        payment = spy(payment);
        when(payment.getId()).thenReturn(1L);
    }

    // === createPayment tests ===

    @Nested
    @DisplayName("createPayment")
    class CreatePayment {

        @Test
        void shouldSucceedForConfirmedBooking() throws StripeException {
            // Booking must be CONFIRMED for payment to proceed
            booking.confirm(booking.getVendor().getUser());
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(paymentRepository.existsByBookingId(1L)).thenReturn(false);

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getId()).thenReturn("pi_test123");
            when(mockIntent.getClientSecret()).thenReturn("cs_test123_secret");
            when(stripeClient.createPaymentIntent(any(Money.class), eq(1L))).thenReturn(mockIntent);

            Payment savedPayment = new Payment(booking, Money.of(11000));
            savedPayment = spy(savedPayment);
            when(savedPayment.getId()).thenReturn(1L);
            when(paymentTransactionService.createPaymentWithBookingUpdate(anyLong(), anyLong(), anyString(), anyString()))
                    .thenReturn(savedPayment);

            // Execute
            String clientSecret = paymentService.createPayment(1L, 1L, "card");

            // Verify
            assertThat(clientSecret).isEqualTo("cs_test123_secret");
            verify(stripeClient).createPaymentIntent(any(Money.class), eq(1L));
            verify(paymentTransactionService).createPaymentWithBookingUpdate(eq(1L), eq(1L), eq("pi_test123"), eq("card"));
        }

        @Test
        void shouldThrowWhenBookingNotFound() {
            when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Booking not found");
        }

        @Test
        void shouldThrowWhenNotBookingOwner() {
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> paymentService.createPayment(2L, 1L, "card"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only pay for your own bookings");
        }

        @Test
        void shouldThrowWhenBookingNotConfirmedStatus() {
            // Booking is still PENDING — not eligible for payment
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Booking is not eligible for payment");
        }

        @Test
        void shouldThrowWhenPaymentAlreadyExists() {
            booking.confirm(booking.getVendor().getUser());
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(paymentRepository.existsByBookingId(1L)).thenReturn(true);

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Payment");
        }

        @Test
        void shouldThrowWhenStripeApiFails() throws StripeException {
            booking.confirm(booking.getVendor().getUser());
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(paymentRepository.existsByBookingId(1L)).thenReturn(false);

            StripeException stripeException = mock(StripeException.class);
            when(stripeException.getMessage()).thenReturn("API error");
            when(stripeClient.createPaymentIntent(any(Money.class), eq(1L)))
                    .thenThrow(stripeException);

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(StripeApiException.class)
                    .hasMessageContaining("Failed to create payment intent");
        }
    }

    // === handlePaymentSucceeded tests ===

    @Nested
    @DisplayName("handlePaymentSucceeded")
    class HandlePaymentSucceeded {

        @Test
        void shouldSucceedForProcessingPayment() {
            // Booking must be CONFIRMED so it can be marked PAID by the webhook
            booking.confirm(booking.getVendor().getUser());
            // Payment in PROCESSING status
            payment.markAsProcessing();
            payment.setStripePaymentIntentId("pi_test123");
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
            when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

            // Execute
            paymentService.handlePaymentSucceeded("pi_test123");

            // Verify
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            // Booking should now be PAID (webhook updated it)
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);
            verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
        }

        @Test
        void shouldThrowWhenPaymentNotFound() {
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.handlePaymentSucceeded("pi_test123"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment not found");
        }

        /**
         * INTENTIONAL BEHAVIOR: When payment_intent.succeeded arrives but no local Payment exists,
         * we throw ResourceNotFoundException to trigger Stripe retry.
         */
        @Test
        @DisplayName("handlePaymentSucceeded throws when Payment not found - triggers Stripe retry (intentional)")
        void handlePaymentSucceededThrowsWhenPaymentNotFoundTriggersRetry() {
            when(paymentRepository.findByStripePaymentIntentId("pi_unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.handlePaymentSucceeded("pi_unknown"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment not found");
        }

        @Test
        void shouldThrowWhenPaymentNotProcessing() {
            // Payment still PENDING (not yet sent to Stripe)
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.handlePaymentSucceeded("pi_test123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition payment from PENDING to SUCCEEDED");
        }
    }

    // === handlePaymentFailed tests ===

    @Nested
    @DisplayName("handlePaymentFailed")
    class HandlePaymentFailed {

        @Test
        void shouldSucceedForProcessingPayment() {
            payment.markAsProcessing();
            payment.setStripePaymentIntentId("pi_test123");
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

            paymentService.handlePaymentFailed("pi_test123", "card_declined", "declined");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
        }

        @Test
        void shouldThrowWhenPaymentNotFound() {
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                paymentService.handlePaymentFailed("pi_test123", "error", "code")
            ).isInstanceOf(ResourceNotFoundException.class)
             .hasMessageContaining("Payment not found");
        }
    }

    // === getPayment tests ===

    @Nested
    @DisplayName("getPayment")
    class GetPayment {

        @Test
        void shouldSucceedForPaymentOwner() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentService.getPayment(1L, 1L);

            assertThat(result).isEqualTo(payment);
        }

        @Test
        void shouldThrowWhenPaymentNotFound() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(1L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment not found");
        }

        @Test
        void shouldThrowWhenNotPaymentOwner() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.getPayment(2L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only view your own payments");
        }
    }

    // === Authorization tests ===

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        void createPaymentRequiresBookingOwnership() {
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> paymentService.createPayment(999L, 1L, "card"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only pay for your own bookings");
        }

        @Test
        void getPaymentRequiresPaymentOwnership() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.getPayment(999L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only view your own payments");
        }
    }
}
