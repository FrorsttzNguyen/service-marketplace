package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.OrderMapper;
import com.hien.marketplace.config.CommissionProperties;
import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.domain.order.OrderStatus;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.BookingRepository;
import com.hien.marketplace.infrastructure.persistence.OrderRepository;
import com.hien.marketplace.interfaces.dto.response.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService — the booking → order glue (Phase 4).
 *
 * WHY test the service (not the controller): the real logic — authorization, the CONFIRMED status
 * gate, idempotency on bookingId, and commission math — lives here. Mocking the repositories lets
 * us assert each branch precisely without spinning up a DB.
 *
 * MOCKING mirrors PaymentServiceTest:
 *  - BookingRepository / OrderRepository: DB ops
 *  - OrderMapper: entity → DTO (stubbed to a recognizable response)
 *  - CommissionProperties: real bean with rate = 0.10 so commission math is deterministic
 *  - Domain objects spied so we can stub getId() (the entities have no id setter; JPA sets it)
 *
 * @MockitoSettings(LENIENT) allows unused stubs (each @BeforeEach sets up all mocks, not all tests
 * use all stubs).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    // CommissionProperties is a REAL instance (not a @Mock) so the commission math runs for real
    // and is asserted directly. Because @InjectMocks only injects @Mock fields, we construct
    // OrderService by hand in setUp() with the real commission bean wired in.
    private CommissionProperties commissionProperties;

    private OrderService orderService;

    private static final Long CUSTOMER_ID = 1L;
    private static final Long BOOKING_ID = 10L;
    private static final Long ORDER_ID = 100L;

    private User customer;
    private Booking confirmedBooking;
    private Booking pendingBooking;

    @BeforeEach
    void setUp() {
        // Real commission bean (10%) — assertions on order totals depend on this value.
        commissionProperties = new CommissionProperties();
        commissionProperties.setRate(new BigDecimal("0.10"));

        // Wire the service manually so the real CommissionProperties is used (not a null mock).
        orderService = new OrderService(bookingRepository, orderRepository, orderMapper, commissionProperties);

        // Customer with a spied id (JPA sets ids; in a unit test we stub them).
        customer = new User("customer@example.com", "hashed", "Customer", UserRole.CUSTOMER);
        customer = spy(customer);
        when(customer.getId()).thenReturn(CUSTOMER_ID);

        // Vendor + service (only needed so the Booking constructor is satisfied).
        User vendorUser = new User("vendor@example.com", "hashed", "Vendor", UserRole.VENDOR);
        Vendor vendor = new Vendor(vendorUser, "Vendor Biz");
        ServiceEntity service = new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60);

        // A CONFIRMED booking with a $100.00 total (10000 cents).
        confirmedBooking = new Booking(service, customer, vendor, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), Money.of(10000));
        confirmedBooking.confirm(vendorUser); // PENDING → CONFIRMED
        confirmedBooking = spy(confirmedBooking);
        when(confirmedBooking.getId()).thenReturn(BOOKING_ID);

        // A still-PENDING booking for the status-gate test.
        pendingBooking = new Booking(service, customer, vendor, LocalDate.now(),
                LocalTime.of(12, 0), LocalTime.of(13, 0), Money.of(10000));
        pendingBooking = spy(pendingBooking);
        when(pendingBooking.getId()).thenReturn(BOOKING_ID);
    }

    // === createFromBooking ==================================================

    @Nested
    @DisplayName("createFromBooking")
    class CreateFromBooking {

        @Test
        @DisplayName("happy path: CONFIRMED booking → order created with correct subtotal/commission/total")
        void shouldCreateOrderForConfirmedBooking() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
            when(orderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            // Capture the persisted Order so we can assert the money math on the real object.
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            when(orderRepository.save(captor.capture())).thenAnswer(inv -> {
                Order saved = inv.getArgument(0);
                Order spied = spy(saved);
                when(spied.getId()).thenReturn(ORDER_ID);
                return spied;
            });
            when(orderMapper.toResponse(any(Order.class)))
                    .thenReturn(new OrderResponse(ORDER_ID, BOOKING_ID, CUSTOMER_ID, null,
                            new BigDecimal("110.00"), "VND", OrderStatus.CREATED, "STRIPE", null, null, null));

            OrderResponse response = orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID);

            // Money: subtotal = booking total (10000c), commission = 10% = 1000c, total = 11000c.
            Order created = captor.getValue();
            assertThat(created.getSubtotal().getAmountCents()).isEqualTo(10000L);
            assertThat(created.getCommission().getAmountCents()).isEqualTo(1000L);
            assertThat(created.getTotal().getAmountCents()).isEqualTo(11000L);
            assertThat(created.getStatus()).isEqualTo(OrderStatus.CREATED);

            assertThat(response.id()).isEqualTo(ORDER_ID);
            assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        }

        @Test
        @DisplayName("booking not found → 404 ResourceNotFoundException")
        void shouldThrowWhenBookingNotFound() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Booking not found");
        }

        @Test
        @DisplayName("caller is not the booking's customer → 422 BusinessRuleViolationException")
        void shouldThrowWhenCallerIsNotCustomer() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));

            assertThatThrownBy(() -> orderService.createFromBooking(999L, BOOKING_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("your own bookings");
        }

        @Test
        @DisplayName("booking not CONFIRMED → 422 BusinessRuleViolationException")
        void shouldThrowWhenBookingNotConfirmed() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBooking));
            when(orderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Booking must be confirmed");
        }

        @Test
        @DisplayName("idempotent: second call for the same booking returns the SAME order, no duplicate row")
        void shouldReturnExistingPayableOrderWithoutCreatingADuplicate() {
            // An existing CREATED order for this booking (e.g. from a previous call).
            Order existing = new Order(customer, confirmedBooking, Money.of(10000), Money.of(1000));
            existing = spy(existing);
            when(existing.getId()).thenReturn(ORDER_ID);
            when(existing.getStatus()).thenReturn(OrderStatus.CREATED);

            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
            when(orderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existing));
            when(orderMapper.toResponse(existing))
                    .thenReturn(new OrderResponse(ORDER_ID, BOOKING_ID, CUSTOMER_ID, null,
                            new BigDecimal("110.00"), "VND", OrderStatus.CREATED, "STRIPE", null, null, null));

            OrderResponse first = orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID);
            OrderResponse second = orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID);

            // Both calls return the same order id, and save() is never called (no new row).
            assertThat(second.id()).isEqualTo(ORDER_ID).isEqualTo(first.id());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("existing order in a terminal/paid status → 422 conflict, not reused")
        void shouldRejectWhenExistingOrderIsNoLongerPayable() {
            Order paid = new Order(customer, confirmedBooking, Money.of(10000), Money.of(1000));
            paid = spy(paid);
            when(paid.getId()).thenReturn(ORDER_ID);
            when(paid.getStatus()).thenReturn(OrderStatus.PAID); // already paid → not payable

            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
            when(orderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(paid));

            assertThatThrownBy(() -> orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("no longer payable");
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("commission math: $19.99 subtotal × 10% → 200 cents (HALF_UP on 199.9)")
        void shouldRoundCommissionHalfUpInCents() {
            // Override booking total to $19.99 = 1999 cents. 1999 × 0.10 = 199.9 → HALF_UP → 200 cents.
            Money subtotal = Money.of(1999);
            Booking booking = new Booking(
                    confirmedBooking.getService(), customer, confirmedBooking.getVendor(),
                    LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(10, 0), subtotal);
            booking.confirm(confirmedBooking.getVendor().getUser());
            booking = spy(booking);
            when(booking.getId()).thenReturn(BOOKING_ID);

            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(orderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            when(orderRepository.save(captor.capture())).thenAnswer(inv -> {
                Order saved = inv.getArgument(0);
                Order spied = spy(saved);
                when(spied.getId()).thenReturn(ORDER_ID);
                return spied;
            });
            when(orderMapper.toResponse(any(Order.class)))
                    .thenReturn(new OrderResponse(ORDER_ID, BOOKING_ID, CUSTOMER_ID, null,
                            BigDecimal.ZERO, "VND", OrderStatus.CREATED, "STRIPE", null, null, null));

            orderService.createFromBooking(CUSTOMER_ID, BOOKING_ID);

            Order created = captor.getValue();
            assertThat(created.getSubtotal().getAmountCents()).isEqualTo(1999L);
            assertThat(created.getCommission().getAmountCents()).isEqualTo(200L); // 199.9 → 200
            assertThat(created.getTotal().getAmountCents()).isEqualTo(2199L);
        }
    }

    // === getOrder ===========================================================

    @Nested
    @DisplayName("getOrder")
    class GetOrder {

        @Test
        @DisplayName("owner reads their own order → 200")
        void shouldReturnOrderForOwner() {
            Order order = new Order(customer, confirmedBooking, Money.of(10000), Money.of(1000));
            order = spy(order);
            when(order.getId()).thenReturn(ORDER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderMapper.toResponse(order))
                    .thenReturn(new OrderResponse(ORDER_ID, BOOKING_ID, CUSTOMER_ID, null,
                            new BigDecimal("110.00"), "VND", OrderStatus.CREATED, "STRIPE", null, null, null));

            OrderResponse response = orderService.getOrder(CUSTOMER_ID, ORDER_ID);

            assertThat(response.id()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("order not found → 404")
        void shouldThrowWhenOrderNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(CUSTOMER_ID, ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }

        @Test
        @DisplayName("non-owner → 422")
        void shouldThrowWhenCallerIsNotOrderOwner() {
            Order order = new Order(customer, confirmedBooking, Money.of(10000), Money.of(1000));
            order = spy(order);
            when(order.getId()).thenReturn(ORDER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.getOrder(999L, ORDER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("your own orders");
        }
    }
}
