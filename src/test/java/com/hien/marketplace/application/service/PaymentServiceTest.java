package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.*;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.domain.order.OrderStatus;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.OrderRepository;
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
 * Unit tests cho PaymentService.
 *
 * WHY test service:
 * - Service chứa business logic phức tạp
 * - Authorization checks
 * - Stripe API integration
 * - Transaction boundaries
 *
 * MOCKING:
 * - StripeClient: External API calls
 * - PaymentRepository: Database operations
 * - OrderRepository: Database operations
 * - ApplicationEventPublisher: Event publishing
 *
 * @MockitoSettings(strictness = Strictness.LENIENT) allows unused stubs
 * (needed because @BeforeEach sets up all mocks, but not all tests use all stubs)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private User customer;
    private Booking booking;
    private Order order;
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

        // Create booking
        booking = new Booking(service, customer, vendor, LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(11, 0), Money.of(10000));
        booking = spy(booking);
        when(booking.getId()).thenReturn(1L);

        // Create order
        order = new Order(customer, booking, Money.of(10000), Money.of(1000));
        order = spy(order);
        when(order.getId()).thenReturn(1L);

        payment = new Payment(order, Money.of(11000));
        payment = spy(payment);
        when(payment.getId()).thenReturn(1L);
    }

    // === createPayment tests ===

    @Nested
    @DisplayName("createPayment")
    class CreatePayment {

        @Test
        void shouldSucceedForValidOrder() throws StripeException {
            // Setup
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.existsByOrderId(1L)).thenReturn(false);

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getId()).thenReturn("pi_test123");
            when(mockIntent.getClientSecret()).thenReturn("cs_test123_secret");
            when(stripeClient.createPaymentIntent(any(Money.class), eq(1L))).thenReturn(mockIntent);

            Payment savedPayment = new Payment(order, Money.of(11000));
            savedPayment = spy(savedPayment);
            when(savedPayment.getId()).thenReturn(1L);
            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(savedPayment));

            // Execute
            String clientSecret = paymentService.createPayment(1L, 1L, "card");

            // Verify
            assertThat(clientSecret).isEqualTo("cs_test123_secret");
            verify(stripeClient).createPaymentIntent(any(Money.class), eq(1L));
            verify(paymentRepository).save(any(Payment.class));
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        void shouldThrowWhenOrderNotFound() {
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }

        @Test
        void shouldThrowWhenNotOrderOwner() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.createPayment(2L, 1L, "card"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only pay for your own orders");
        }

        @Test
        void shouldThrowWhenOrderNotCreatedStatus() {
            order.markAsPendingPayment();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Order is not eligible for payment");
        }

        @Test
        void shouldThrowWhenPaymentAlreadyExists() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.existsByOrderId(1L)).thenReturn(true);

            assertThatThrownBy(() -> paymentService.createPayment(1L, 1L, "card"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Payment");
        }

        @Test
        void shouldThrowWhenStripeApiFails() throws StripeException {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.existsByOrderId(1L)).thenReturn(false);

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
            // Setup - order must be in PENDING_PAYMENT status (created by createPayment)
            order.markAsPendingPayment();
            // Payment in PROCESSING status
            payment.markAsProcessing();
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
            when(orderRepository.save(any(Order.class))).thenReturn(order);

            // Execute
            paymentService.handlePaymentSucceeded("pi_test123");

            // Verify
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        void shouldThrowWhenPaymentNotFound() {
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.handlePaymentSucceeded("pi_test123"))
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
            when(paymentRepository.findByStripePaymentIntentId("pi_test123"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

            paymentService.handlePaymentFailed("pi_test123", "card_declined", "declined");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(eventPublisher).publishEvent(any());
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
        void createPaymentRequiresOrderOwnership() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.createPayment(999L, 1L, "card"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only pay for your own orders");
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
