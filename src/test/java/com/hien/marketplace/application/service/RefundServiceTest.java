package com.hien.marketplace.application.service;

import com.hien.marketplace.application.dto.RefundContext;
import com.hien.marketplace.application.exception.*;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.payment.Payment;
import com.hien.marketplace.domain.payment.PaymentStatus;
import com.hien.marketplace.domain.payment.Refund;
import com.hien.marketplace.domain.payment.RefundStatus;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.PaymentRepository;
import com.hien.marketplace.infrastructure.persistence.RefundRepository;
import com.hien.marketplace.infrastructure.stripe.StripeClient;
import com.stripe.exception.StripeException;
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
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho RefundService (after Order→Booking merge).
 *
 * After the merge: Payment references Booking directly (no Order).
 * RefundContext uses BookingStatus instead of OrderStatus.
 * isRefundable() requires bookingStatus == PAID (not OrderStatus.PAID).
 *
 * @MockitoSettings(strictness = Strictness.LENIENT) allows unused stubs
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RefundTransactionService refundTransactionService;

    @InjectMocks
    private RefundService refundService;

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

        // Create booking with its own service address for this appointment.
        booking = new Booking(service, customer, vendor, LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(11, 0),
                Money.of(10000), Money.of(1000), new Address("123 Service Street", "Test City", "70000"));
        booking = spy(booking);
        when(booking.getId()).thenReturn(1L);

        // Payment now references Booking directly (not Order)
        payment = new Payment(booking, Money.of(11000));
        payment = spy(payment);
        when(payment.getId()).thenReturn(1L);
        when(payment.getStripePaymentIntentId()).thenReturn("pi_test123");

        // Payment needs to be SUCCEEDED for refund
        payment.markAsProcessing();
        payment.markAsSucceeded();

        // Booking must be PAID for refund (new state replacing Order.PAID)
        booking.confirm(vendorUser);
        booking.markAsPaid(null);

        // Initialize empty refunds list
        when(payment.getRefunds()).thenReturn(new ArrayList<>());
    }

    // Helper method to create RefundContext from payment
    // RefundContext now uses BookingStatus instead of OrderStatus
    private RefundContext createRefundContext(Payment payment, Money alreadyRefunded) {
        return new RefundContext(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getBooking().getId(),
                payment.getBooking().getStatus(),
                payment.getBooking().getCustomer().getId(),
                alreadyRefunded
        );
    }

    // === createRefund tests ===

    @Nested
    @DisplayName("createRefund")
    class CreateRefund {

        @Test
        void shouldSucceedForFullRefund() throws StripeException {
            // Setup - mock loadRefundContext via RefundTransactionService
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            com.stripe.model.Refund mockStripeRefund = mock(com.stripe.model.Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test123");
            when(stripeClient.createRefund(eq("pi_test123"), eq(null))).thenReturn(mockStripeRefund);

            Refund savedRefund = new Refund(payment, payment.getAmount(), "Full refund");
            savedRefund.markAsSucceeded();
            savedRefund = spy(savedRefund);
            when(savedRefund.getId()).thenReturn(1L);
            when(refundTransactionService.createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test123")))
                    .thenReturn(savedRefund);

            // Execute
            Refund result = refundService.createRefund(1L, 1L, null, "Full refund");

            // Verify
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
            assertThat(result.getAmount()).isEqualTo(payment.getAmount());
            verify(stripeClient).createRefund("pi_test123", null); // null = full refund
            verify(refundTransactionService).createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test123"));
        }

        @Test
        void shouldSucceedForPartialRefund() throws StripeException {
            // Setup - mock loadRefundContext via RefundTransactionService
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            com.stripe.model.Refund mockStripeRefund = mock(com.stripe.model.Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test123");
            when(stripeClient.createRefund(eq("pi_test123"), eq(5000L))).thenReturn(mockStripeRefund);

            Refund savedRefund = new Refund(payment, Money.of(5000), "Partial refund");
            savedRefund.markAsSucceeded();
            savedRefund = spy(savedRefund);
            when(savedRefund.getId()).thenReturn(1L);
            when(refundTransactionService.createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test123")))
                    .thenReturn(savedRefund);

            // Execute
            Refund result = refundService.createRefund(1L, 1L, 5000L, "Partial refund");

            // Verify
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
            assertThat(result.getAmount()).isEqualTo(Money.of(5000));
            verify(stripeClient).createRefund("pi_test123", 5000L);
            verify(refundTransactionService).createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test123"));
        }

        @Test
        void shouldUpdateBookingStatusOnFullRefund() throws StripeException {
            // Setup - mock loadRefundContext via RefundTransactionService
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            com.stripe.model.Refund mockStripeRefund = mock(com.stripe.model.Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test123");
            when(stripeClient.createRefund(eq("pi_test123"), eq(null))).thenReturn(mockStripeRefund);

            // Mock the transaction service to update booking status (PAID → REFUNDED)
            final Refund savedRefund = new Refund(payment, payment.getAmount(), "Full refund");
            savedRefund.markAsSucceeded();
            when(refundTransactionService.createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test123")))
                    .thenAnswer(invocation -> {
                        // Simulate the booking status update that happens in transaction service
                        booking.refund(null, "Full refund");
                        return savedRefund;
                    });

            // Execute
            refundService.createRefund(1L, 1L, null, "Full refund");

            // Verify booking status updated
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.REFUNDED);
        }

        @Test
        void shouldNotUpdateBookingStatusOnPartialRefund() throws StripeException {
            // Setup - mock loadRefundContext via RefundTransactionService
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            com.stripe.model.Refund mockStripeRefund = mock(com.stripe.model.Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test123");
            when(stripeClient.createRefund(eq("pi_test123"), eq(5000L))).thenReturn(mockStripeRefund);

            // Mock the transaction service (partial refund doesn't update booking)
            Refund savedRefund = new Refund(payment, Money.of(5000), "Partial refund");
            savedRefund.markAsSucceeded();
            savedRefund = spy(savedRefund);
            when(savedRefund.getId()).thenReturn(1L);
            when(refundTransactionService.createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test123")))
                    .thenReturn(savedRefund);

            // Execute
            refundService.createRefund(1L, 1L, 5000L, "Partial refund");

            // Verify booking status NOT updated (still PAID)
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);
        }

        @Test
        void shouldThrowWhenPaymentNotFound() {
            when(refundTransactionService.loadRefundContext(1L, 1L))
                    .thenThrow(new ResourceNotFoundException("Payment", 1L));

            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, null, "Refund"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment not found");
        }

        @Test
        void shouldThrowWhenNotPaymentOwner() {
            // Load context with customer ID 1 (payment belongs to customer 1)
            RefundContext context = createRefundContext(payment, Money.of(0));
            // When user 2 tries to refund, loadRefundContext is called with (paymentId=1L, userId=2L)
            when(refundTransactionService.loadRefundContext(1L, 2L)).thenReturn(context);

            // Try to refund as user 2 (not owner)
            assertThatThrownBy(() -> refundService.createRefund(2L, 1L, null, "Refund"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only refund your own payments");
        }

        @Test
        void shouldThrowWhenBookingNotPaid() {
            // RefundContext with booking in CONFIRMED status (not PAID) — isRefundable() returns false
            RefundContext context = new RefundContext(
                    1L, "pi_test123", PaymentStatus.SUCCEEDED, Money.of(11000),
                    1L, BookingStatus.CONFIRMED, 1L, Money.of(0)
            );
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, null, "Refund"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Only succeeded payments on paid bookings can be refunded");
        }

        @Test
        void shouldThrowWhenRefundAmountExceedsPayment() throws StripeException {
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            // Payment amount is 11000, try to refund 15000
            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, 15000L, "Too much"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("cannot exceed");
        }

        @Test
        void shouldThrowWhenRefundAmountIsZero() {
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, 0L, "Zero amount"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Refund amount must be positive");
        }

        @Test
        void shouldThrowWhenRefundAmountIsNegative() {
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            // Money.of() throws IllegalArgumentException for negative amounts
            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, -100L, "Negative amount"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Money amount cannot be negative");
        }

        @Test
        void shouldThrowWhenStripeApiFails() throws StripeException {
            RefundContext context = createRefundContext(payment, Money.of(0));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            StripeException stripeException = mock(StripeException.class);
            when(stripeException.getMessage()).thenReturn("API error");
            when(stripeClient.createRefund(anyString(), any()))
                    .thenThrow(stripeException);

            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, null, "Refund"))
                    .isInstanceOf(StripeApiException.class)
                    .hasMessageContaining("Failed to create refund");
        }

        @Test
        void shouldValidateTotalRefundsAcrossMultipleRefunds() throws StripeException {
            // Setup: already refunded 5000
            Refund existingRefund = new Refund(payment, Money.of(5000), "First refund");
            existingRefund.markAsSucceeded();
            when(payment.getRefunds()).thenReturn(new ArrayList<>(java.util.List.of(existingRefund)));

            RefundContext context = createRefundContext(payment, Money.of(5000));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            // Try to refund another 7000 (5000 + 7000 = 12000 > 11000)
            assertThatThrownBy(() -> refundService.createRefund(1L, 1L, 7000L, "Second refund"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("cannot exceed");
        }

        @Test
        void shouldAllowMultiplePartialRefundsWithinLimit() throws StripeException {
            // Setup: already refunded 5000
            Refund existingRefund = new Refund(payment, Money.of(5000), "First refund");
            existingRefund.markAsSucceeded();
            when(payment.getRefunds()).thenReturn(new ArrayList<>(java.util.List.of(existingRefund)));

            RefundContext context = createRefundContext(payment, Money.of(5000));
            when(refundTransactionService.loadRefundContext(1L, 1L)).thenReturn(context);

            com.stripe.model.Refund mockStripeRefund = mock(com.stripe.model.Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test456");
            when(stripeClient.createRefund(eq("pi_test123"), eq(6000L))).thenReturn(mockStripeRefund);

            Refund savedRefund = new Refund(payment, Money.of(6000), "Second refund");
            savedRefund.markAsSucceeded();
            savedRefund = spy(savedRefund);
            when(savedRefund.getId()).thenReturn(2L);
            when(refundTransactionService.createRefundWithBookingUpdate(eq(1L), any(), anyString(), eq("re_test456")))
                    .thenReturn(savedRefund);

            // Execute: refund 6000 more (5000 + 6000 = 11000 = payment amount, allowed)
            Refund result = refundService.createRefund(1L, 1L, 6000L, "Second refund");

            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualTo(Money.of(6000));
        }
    }

    // === getRefund tests ===

    @Nested
    @DisplayName("getRefund")
    class GetRefund {

        @Test
        void shouldSucceedForRefundOwner() {
            Refund refund = new Refund(payment, Money.of(5000), "Refund");
            refund = spy(refund);
            when(refund.getId()).thenReturn(1L);
            when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

            Refund result = refundService.getRefund(1L, 1L);

            assertThat(result).isEqualTo(refund);
        }

        @Test
        void shouldThrowWhenRefundNotFound() {
            when(refundRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.getRefund(1L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Refund not found");
        }

        @Test
        void shouldThrowWhenNotRefundOwner() {
            Refund refund = new Refund(payment, Money.of(5000), "Refund");
            refund = spy(refund);
            when(refund.getId()).thenReturn(1L);
            when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.getRefund(2L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only view your own refunds");
        }
    }

    // === getRefundsForPayment tests ===

    @Nested
    @DisplayName("getRefundsForPayment")
    class GetRefundsForPayment {

        @Test
        void shouldSucceedForPaymentOwner() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Refund refund1 = new Refund(payment, Money.of(5000), "First");
            Refund refund2 = new Refund(payment, Money.of(6000), "Second");
            when(refundRepository.findByPaymentId(1L)).thenReturn(java.util.List.of(refund1, refund2));

            java.util.List<Refund> result = refundService.getRefundsForPayment(1L, 1L);

            assertThat(result).hasSize(2);
        }

        @Test
        void shouldThrowWhenPaymentNotFound() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.getRefundsForPayment(1L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment not found");
        }

        @Test
        void shouldThrowWhenNotPaymentOwner() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> refundService.getRefundsForPayment(2L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only view refunds for your own payments");
        }
    }

    // === Authorization tests ===

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        void createRefundRequiresPaymentOwnership() {
            RefundContext context = createRefundContext(payment, Money.of(0));
            // When user 999 tries to refund, loadRefundContext is called with (paymentId=1L, userId=999L)
            when(refundTransactionService.loadRefundContext(1L, 999L)).thenReturn(context);

            assertThatThrownBy(() -> refundService.createRefund(999L, 1L, null, "Refund"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only refund your own payments");
        }

        @Test
        void getRefundRequiresRefundOwnership() {
            Refund refund = new Refund(payment, Money.of(5000), "Refund");
            refund = spy(refund);
            when(refund.getId()).thenReturn(1L);
            when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

            assertThatThrownBy(() -> refundService.getRefund(999L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only view your own refunds");
        }

        @Test
        void getRefundsForPaymentRequiresPaymentOwnership() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> refundService.getRefundsForPayment(999L, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("You can only view refunds for your own payments");
        }
    }
}
