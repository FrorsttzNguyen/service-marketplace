package com.hien.marketplace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.interfaces.dto.request.LoginRequest;
import com.hien.marketplace.interfaces.dto.request.RegisterRequest;
import com.hien.marketplace.interfaces.dto.response.AuthResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 *
 * WHY @SpringBootTest: Load full Spring context (all beans, config, security).
 * WHY @AutoConfigureMockMvc: Setup MockMvc for HTTP requests without real server.
 * WHY @ActiveProfiles("test"): Use H2 in-memory database, not PostgreSQL.
 *
 * Test scenarios:
 * 1. Register → Login → Protected endpoint flow
 * 2. Duplicate email rejection
 * 3. Invalid credentials rejection
 * 4. Validation errors (blank fields, invalid email, weak password)
 *
 * MockMvc vs TestRestTemplate:
 * - MockMvc: Lightweight, no real HTTP server, faster
 * - TestRestTemplate: Starts embedded server, real HTTP calls
 * - For controller tests, MockMvc is standard choice
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;  // JSON serialization

    // ================================================================
    // Register Tests
    // ================================================================

    @Nested
    @DisplayName("Register Endpoint")
    class RegisterTests {

        @Test
        @DisplayName("Should register new customer successfully")
        void shouldRegisterCustomerSuccessfully() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Test User",
                    "test-customer@example.com",
                    "Password123",
                    "+84123456789",
                    false  // Not registering as vendor
            );

            MvcResult result = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").exists())
                    .andExpect(jsonPath("$.email").value("test-customer@example.com"))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"))
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andReturn();

            // Verify response contains valid JWT tokens
            String responseBody = result.getResponse().getContentAsString();
            AuthResponse response = objectMapper.readValue(responseBody, AuthResponse.class);

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.accessTokenExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("Should register new vendor with registerAsVendor=true")
        void shouldRegisterVendorSuccessfully() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Vendor User",
                    "test-vendor@example.com",
                    "Password123",
                    "+84987654321",
                    true  // Registering as vendor
            );

            MvcResult result = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("VENDOR"))
                    .andReturn();

            // Verify vendor profile was created (check by trying to access vendor endpoints)
            AuthResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    AuthResponse.class
            );

            // Vendor can now access /api/bookings/vendor endpoint
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/vendor")
                            .header("Authorization", "Bearer " + response.accessToken()))
                    .andExpect(status().isOk());  // Not 422 (vendor profile not found)
        }

        @Test
        @DisplayName("Should reject duplicate email")
        void shouldRejectDuplicateEmail() throws Exception {
            // First registration succeeds
            RegisterRequest request1 = new RegisterRequest(
                    "User One",
                    "duplicate@example.com",
                    "Password123",
                    "+84111111111",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // Second registration with same email fails
            RegisterRequest request2 = new RegisterRequest(
                    "User Two",
                    "duplicate@example.com",
                    "Password456",
                    "+84222222222",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isConflict())  // 409 Conflict
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("Should reject blank full name")
        void shouldRejectBlankFullName() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "",  // Blank name
                    "blank-name@example.com",
                    "Password123",
                    "+84123456789",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details").exists());
        }

        @Test
        @DisplayName("Should reject invalid email format")
        void shouldRejectInvalidEmail() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Test User",
                    "invalid-email",  // Not a valid email
                    "Password123",
                    "+84123456789",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details").exists());
        }

        @Test
        @DisplayName("Should reject weak password (no uppercase, no digit)")
        void shouldRejectWeakPassword() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Test User",
                    "weak-pass@example.com",
                    "password",  // No uppercase, no digit
                    "+84123456789",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details").exists());
        }

        @Test
        @DisplayName("Should reject invalid phone number (too short)")
        void shouldRejectInvalidPhone() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Test User",
                    "invalid-phone@example.com",
                    "Password123",
                    "123",  // Too short, will fail validation in PhoneNumber constructor
                    false
            );

            // PhoneNumber constructor throws IllegalArgumentException for short phone
            // This becomes 500 Internal Server Error (caught by generic handler)
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }
    }

    // ================================================================
    // Login Tests
    // ================================================================

    @Nested
    @DisplayName("Login Endpoint")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLoginSuccessfully() throws Exception {
            // First, register a user
            RegisterRequest registerRequest = new RegisterRequest(
                    "Login Test User",
                    "login-test@example.com",
                    "Password123",
                    "+84123456789",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            // Then, login with same credentials
            LoginRequest loginRequest = new LoginRequest(
                    "login-test@example.com",
                    "Password123"
            );

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.email").value("login-test@example.com"));
        }

        @Test
        @DisplayName("Should reject login with wrong password")
        void shouldRejectWrongPassword() throws Exception {
            // Register user
            RegisterRequest registerRequest = new RegisterRequest(
                    "Wrong Pass User",
                    "wrong-pass@example.com",
                    "Password123",
                    "+84123456789",
                    false
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            // Login with wrong password
            LoginRequest loginRequest = new LoginRequest(
                    "wrong-pass@example.com",
                    "WrongPassword456"
            );

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());  // 401 Unauthorized
        }

        @Test
        @DisplayName("Should reject login with non-existent email")
        void shouldRejectNonExistentEmail() throws Exception {
            LoginRequest loginRequest = new LoginRequest(
                    "nonexistent@example.com",
                    "Password123"
            );

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject login with blank fields")
        void shouldRejectBlankFields() throws Exception {
            LoginRequest loginRequest = new LoginRequest(
                    "",  // Blank email
                    ""   // Blank password
            );

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ================================================================
    // Refresh Token Tests
    // ================================================================

    @Nested
    @DisplayName("Refresh Token Endpoint")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh access token successfully")
        void shouldRefreshTokenSuccessfully() throws Exception {
            // Register to get tokens
            RegisterRequest registerRequest = new RegisterRequest(
                    "Refresh Test User",
                    "refresh-test@example.com",
                    "Password123",
                    "+84123456789",
                    false
            );

            MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            AuthResponse authResponse = objectMapper.readValue(
                    registerResult.getResponse().getContentAsString(),
                    AuthResponse.class
            );

            // Use refresh token to get new access token
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + authResponse.refreshToken() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.accessTokenExpiresAt").exists());
        }

        @Test
        @DisplayName("Should reject invalid refresh token")
        void shouldRejectInvalidRefreshToken() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"invalid-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ================================================================
    // Full Auth Flow Test (Register → Login → Protected Endpoint)
    // ================================================================

    @Nested
    @DisplayName("Full Authentication Flow")
    class FullAuthFlowTests {

        @Test
        @DisplayName("Should access protected endpoint after login")
        void shouldAccessProtectedEndpointAfterLogin() throws Exception {
            // Step 1: Register user
            RegisterRequest registerRequest = new RegisterRequest(
                    "Flow Test User",
                    "flow-test@example.com",
                    "Password123",
                    "+84123456789",
                    false
            );

            MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            AuthResponse authResponse = objectMapper.readValue(
                    registerResult.getResponse().getContentAsString(),
                    AuthResponse.class
            );

            // Step 2: Use access token to call protected endpoint
            // /api/bookings requires authentication
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + authResponse.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceId\":1,\"startTime\":\"2026-06-15T09:00:00\",\"endTime\":\"2026-06-15T10:00:00\"}"))
                    // 404 because service doesn't exist, but auth passed (not 401)
                    .andExpect(status().isNotFound());  // Not 401 Unauthorized
        }

        @Test
        @DisplayName("Should reject protected endpoint without token")
        void shouldRejectProtectedEndpointWithoutToken() throws Exception {
            // Spring Security returns 403 Forbidden when authentication is missing
            // (not 401 Unauthorized, because there's no "WWW-Authenticate" header for APIs)
            mockMvc.perform(post("/api/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceId\":1,\"startTime\":\"2026-06-15T09:00:00\",\"endTime\":\"2026-06-15T10:00:00\"}"))
                    .andExpect(status().isForbidden());  // 403 Forbidden
        }

        @Test
        @DisplayName("Should reject protected endpoint with invalid token")
        void shouldRejectProtectedEndpointWithInvalidToken() throws Exception {
            // Invalid JWT token → JwtAuthenticationFilter logs error, no auth set
            // → Spring Security returns 403 Forbidden
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer invalid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceId\":1,\"startTime\":\"2026-06-15T09:00:00\",\"endTime\":\"2026-06-15T10:00:00\"}"))
                    .andExpect(status().isForbidden());  // 403 Forbidden
        }
    }
}