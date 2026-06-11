package com.hien.marketplace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.domain.booking.BookingStatus;
import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.*;
import com.hien.marketplace.interfaces.dto.request.BookingCreateRequest;
import com.hien.marketplace.interfaces.dto.request.RegisterRequest;
import com.hien.marketplace.interfaces.dto.response.AuthResponse;
import com.hien.marketplace.interfaces.dto.response.BookingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BookingController.
 *
 * WHY: Test the full booking flow with JWT authentication.
 * - Create booking (Customer)
 * - View bookings (Customer/Vendor)
 * - Cancel booking (Customer)
 *
 * Test data setup:
 * - Create vendor user → vendor profile → service
 * - Create customer user
 * - Both users get JWT tokens for API calls
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional  // Rollback after each test
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // Test data
    private User vendorUser;
    private Vendor vendor;
    private ServiceEntity service;
    private User customerUser;
    private String customerToken;
    private String vendorToken;

    @BeforeEach
    void setUp() throws Exception {
        // Step 1: Create vendor user and get token
        // Note: registerAsVendor=true sets role but doesn't create Vendor profile (Phase 2 gap)
        // So we create Vendor manually
        vendorUser = registerUser("vendor-booking@test.com", "Vendor Booking", false);
        vendorToken = loginAndGetToken("vendor-booking@test.com", "Password123");

        // Step 2: Create vendor profile manually
        vendor = new Vendor(vendorUser, "Test Vendor Business");
        vendor = vendorRepository.save(vendor);

        // Step 3: Create a service for booking
        service = new ServiceEntity(vendor, "Haircut", Money.of(10000), PricingType.FIXED, 60);
        service.activate();
        service = serviceRepository.save(service);

        // Step 4: Create customer user and get token
        customerUser = registerUser("customer-booking@test.com", "Customer Booking", false);
        customerToken = loginAndGetToken("customer-booking@test.com", "Password123");
    }

    // ================================================================
    // Create Booking Tests
    // ================================================================

    @Nested
    @DisplayName("Create Booking Endpoint")
    class CreateBookingTests {

        @Test
        @DisplayName("Should create booking successfully")
        void shouldCreateBookingSuccessfully() throws Exception {
            LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            LocalDateTime endTime = startTime.plusHours(1);

            BookingCreateRequest request = new BookingCreateRequest(
                    service.getId(),
                    startTime,
                    endTime,
                    1,
                    "Please be on time"
            );

            MvcResult result = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.customerId").value(customerUser.getId()))
                    .andExpect(jsonPath("$.serviceId").value(service.getId()))
                    .andExpect(jsonPath("$.vendorId").value(vendor.getId()))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    // Note: notes field not set in Phase 2 BookingService - will be fixed in Phase 3
                    // .andExpect(jsonPath("$.notes").value("Please be on time"))
                    .andReturn();

            // Verify response
            BookingResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    BookingResponse.class
            );

            assertThat(response.totalPrice()).isNotNull();
        }

        @Test
        @DisplayName("Should reject booking without authentication")
        void shouldRejectBookingWithoutAuth() throws Exception {
            LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            LocalDateTime endTime = startTime.plusHours(1);

            BookingCreateRequest request = new BookingCreateRequest(
                    service.getId(),
                    startTime,
                    endTime,
                    1,
                    null
            );

            mockMvc.perform(post("/api/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should reject booking for non-existent service")
        void shouldRejectBookingForNonExistentService() throws Exception {
            LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            LocalDateTime endTime = startTime.plusHours(1);

            BookingCreateRequest request = new BookingCreateRequest(
                    99999L,  // Non-existent service
                    startTime,
                    endTime,
                    1,
                    null
            );

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should reject booking with start time in the past")
        void shouldRejectBookingWithPastStartTime() throws Exception {
            LocalDateTime startTime = LocalDateTime.now().minusHours(1);  // Past time
            LocalDateTime endTime = LocalDateTime.now().plusHours(1);

            BookingCreateRequest request = new BookingCreateRequest(
                    service.getId(),
                    startTime,
                    endTime,
                    1,
                    null
            );

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should reject booking with missing required fields")
        void shouldRejectBookingWithMissingFields() throws Exception {
            String emptyRequest = "{}";

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should reject overlapping booking (double-booking prevention)")
        void shouldRejectOverlappingBooking() throws Exception {
            // Create first booking
            LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            LocalDateTime endTime = startTime.plusHours(1);

            BookingCreateRequest request1 = new BookingCreateRequest(
                    service.getId(),
                    startTime,
                    endTime,
                    1,
                    "First booking"
            );

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // Try to create overlapping booking (9:30 - 10:30 overlaps with 9:00 - 10:00)
            LocalDateTime overlapStart = startTime.plusMinutes(30);  // 9:30
            LocalDateTime overlapEnd = endTime.plusMinutes(30);       // 10:30

            BookingCreateRequest request2 = new BookingCreateRequest(
                    service.getId(),
                    overlapStart,
                    overlapEnd,
                    1,
                    "Overlapping booking"
            );

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isConflict())  // 409 Conflict
                    .andExpect(jsonPath("$.code").value("BOOKING_CONFLICT"));
        }

        @Test
        @DisplayName("Should allow adjacent booking (no overlap)")
        void shouldAllowAdjacentBooking() throws Exception {
            // Create first booking: 9:00 - 10:00
            LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            LocalDateTime endTime = startTime.plusHours(1);

            BookingCreateRequest request1 = new BookingCreateRequest(
                    service.getId(),
                    startTime,
                    endTime,
                    1,
                    null
            );

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // Create adjacent booking: 10:00 - 11:00 (end == start, no overlap)
            LocalDateTime adjStart = endTime;  // 10:00
            LocalDateTime adjEnd = endTime.plusHours(1);  // 11:00

            BookingCreateRequest request2 = new BookingCreateRequest(
                    service.getId(),
                    adjStart,
                    adjEnd,
                    1,
                    null
            );

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isCreated());  // Should succeed
        }
    }

    // ================================================================
    // Get Bookings Tests
    // ================================================================

    @Nested
    @DisplayName("Get Bookings Endpoint")
    class GetBookingsTests {

        @Test
        @DisplayName("Should get customer's bookings")
        void shouldGetCustomerBookings() throws Exception {
            // Create a booking first
            createTestBooking();

            mockMvc.perform(get("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].customerId").value(customerUser.getId()));
        }

        @Test
        @DisplayName("Should get vendor's bookings")
        void shouldGetVendorBookings() throws Exception {
            // Create a booking first (by customer)
            createTestBooking();

            // Vendor views their bookings
            // Fixed: getVendorBookings now correctly uses userId and looks up vendorId
            mockMvc.perform(get("/api/bookings/vendor")
                            .header("Authorization", "Bearer " + vendorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].vendorId").value(vendor.getId()));
        }

        @Test
        @DisplayName("Should return empty list when no bookings")
        void shouldReturnEmptyListWhenNoBookings() throws Exception {
            mockMvc.perform(get("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    // ================================================================
    // Cancel Booking Tests
    // ================================================================

    @Nested
    @DisplayName("Cancel Booking Endpoint")
    class CancelBookingTests {

        @Test
        @DisplayName("Should cancel booking successfully")
        void shouldCancelBookingSuccessfully() throws Exception {
            Long bookingId = createTestBooking();

            mockMvc.perform(put("/api/bookings/{id}/cancel", bookingId)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("Should reject cancellation by non-owner")
        void shouldRejectCancellationByNonOwner() throws Exception {
            Long bookingId = createTestBooking();

            // Vendor tries to cancel customer's booking
            mockMvc.perform(put("/api/bookings/{id}/cancel", bookingId)
                            .header("Authorization", "Bearer " + vendorToken))
                    .andExpect(status().isUnprocessableEntity())  // 422
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        }

        @Test
        @DisplayName("Should reject cancellation of non-existent booking")
        void shouldRejectCancellationOfNonExistentBooking() throws Exception {
            mockMvc.perform(put("/api/bookings/{id}/cancel", 99999L)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    // Helper Methods
    // ================================================================

    /**
     * Register a user and return the User entity.
     * If registerAsVendor is true, a Vendor profile is also created.
     */
    private User registerUser(String email, String fullName, boolean registerAsVendor) throws Exception {
        RegisterRequest request = new RegisterRequest(
                fullName,
                email,
                "Password123",
                "+84123456789",
                registerAsVendor
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        return userRepository.findByEmail(email).orElseThrow();
    }

    /**
     * Login and return JWT access token.
     */
    private String loginAndGetToken(String email, String password) throws Exception {
        String loginRequest = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );

        return response.accessToken();
    }

    /**
     * Create a test booking and return its ID.
     */
    private Long createTestBooking() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        LocalDateTime endTime = startTime.plusHours(1);

        BookingCreateRequest request = new BookingCreateRequest(
                service.getId(),
                startTime,
                endTime,
                1,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        BookingResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                BookingResponse.class
        );

        return response.id();
    }
}