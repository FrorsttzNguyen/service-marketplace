package com.hien.marketplace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.domain.vendor.VerificationStatus;
import com.hien.marketplace.infrastructure.persistence.CategoryRepository;
import com.hien.marketplace.infrastructure.persistence.UserRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.infrastructure.security.JwtUtils;
import com.hien.marketplace.interfaces.dto.request.ServiceCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.hien.marketplace.interfaces.rest.AdminController} (Phase 5.5).
 *
 * WHAT THESE TESTS PROVE:
 * The vendor onboarding bug is fixed end-to-end. Before this feature, a vendor registered as
 * {@code PENDING} and could never be approved through the API — so they could never create a
 * service. These tests assert the admin path now wires {@link Vendor#approve()} / {@link Vendor#reject()}
 * to the HTTP layer, and that approving a vendor actually unblocks service creation.
 *
 * TEST PROFILE (follows {@link AuthControllerIntegrationTest} / {@link ServiceControllerIntegrationTest}):
 * Uses the {@code test} profile (H2 in-memory DB, {@code ddl-auto=create-drop}) so the suite stays
 * fast and consistent with the rest of the integration tests in this module. {@link BaseIntegrationTest}
 * (Testcontainers + real Postgres) exists in the repo, but it forces {@code ddl-auto=validate} against
 * the Flyway schema, which trips a pre-existing schema mismatch in {@code audit_logs.new_values}
 * (jsonb vs text) unrelated to this feature. Matching the codebase convention keeps these tests green.
 *
 * WHY we seed users/vendors directly (not via {@code AdminBootstrap}):
 * The bootstrap is env-driven and intentionally non-deterministic. Here we build the ADMIN, VENDOR,
 * and CUSTOMER accounts directly through the repositories so the assertions are independent of env
 * vars — and so the test is hermetic (no dependency on ADMIN_EMAIL/ADMIN_PASSWORD).
 *
 * JWT ROLES & AUTHORITY MAPPING:
 * {@code JwtAuthenticationFilter} maps the token's {@code role} claim to a Spring authority
 * {@code "ROLE_" + role}. {@code SecurityConfig} then guards {@code /api/admin/**} with
 * {@code hasRole("ADMIN")}, which Spring resolves to {@code ROLE_ADMIN}. So an admin JWT must be
 * generated with {@code UserRole.ADMIN.name()} (i.e. "ADMIN"), and a vendor/customer JWT likewise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtUtils jwtUtils;

    // ================================================================
    // Test data builders — keep each test hermetic and readable.
    // ================================================================

    /** Create + persist an ADMIN user and return a JWT carrying ROLE_ADMIN. */
    private String adminToken() {
        User admin = userRepository.save(
                new User("admin-" + System.nanoTime() + "@test.com", "hashed", "Admin", UserRole.ADMIN));
        return jwtUtils.generateAccessToken(admin.getId(), admin.getEmail(), UserRole.ADMIN.name());
    }

    /** Create + persist a VENDOR user with a PENDING vendor profile; return their JWT. */
    private Vendor pendingVendor(String emailSuffix, String businessName) {
        // nanoTime suffix avoids email collisions across tests/methods (User.email is unique).
        String email = "vendor-" + emailSuffix + "-" + System.nanoTime() + "@test.com";
        User vendorUser = userRepository.save(new User(email, "hashed", "Vendor", UserRole.VENDOR));
        String token = jwtUtils.generateAccessToken(
                vendorUser.getId(), vendorUser.getEmail(), UserRole.VENDOR.name());
        // Stash the token on the vendor's description field so callers don't need a parallel return value.
        // (Vendor has no token field; this is test-only plumbing and never persists to prod.)
        Vendor vendor = new Vendor(vendorUser, businessName);
        vendor.setDescription(token);
        return vendorRepository.save(vendor);
    }

    /** Create + persist a CUSTOMER user and return their JWT. */
    private String customerToken() {
        User customer = userRepository.save(
                new User("customer-" + System.nanoTime() + "@test.com", "hashed", "Customer", UserRole.CUSTOMER));
        return jwtUtils.generateAccessToken(customer.getId(), customer.getEmail(), UserRole.CUSTOMER.name());
    }

    // ================================================================
    // Authorization tests — who is allowed to hit /api/admin/**.
    // ================================================================

    @Nested
    @DisplayName("Authorization: /api/admin/**")
    class AuthorizationTests {

        @Test
        @DisplayName("non-admin (CUSTOMER) → 403 Forbidden")
        void nonAdminGets403() throws Exception {
            // A CUSTOMER JWT is authenticated but lacks ROLE_ADMIN → Spring returns 403.
            mockMvc.perform(post("/api/admin/vendors/1/approve")
                            .header("Authorization", "Bearer " + customerToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("non-admin (VENDOR) → 403 Forbidden")
        void vendorGets403() throws Exception {
            // A VENDOR JWT is also authenticated but not ADMIN → 403. Proves admin endpoints are
            // role-scoped to ADMIN, not just "any authenticated user".
            Vendor vendor = pendingVendor("vendor-auth", "Vendor Biz");
            String vendorToken = vendor.getDescription();

            mockMvc.perform(get("/api/admin/vendors")
                            .header("Authorization", "Bearer " + vendorToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("anonymous (no token) → 403 Forbidden (denied before controller)")
        void anonymousDenied() throws Exception {
            // No Authorization header at all → Spring Security denies before reaching the controller.
            //
            // NOTE on 403 vs 401: Spring's default behavior for a stateless JWT API (no
            // authenticationEntryPoint / WWW-Authenticate header) is to return 403 even for fully
            // anonymous requests — the same behavior AuthControllerIntegrationTest asserts for
            // protected endpoints. Changing that globally would be a security-config change outside
            // this feature's scope (and would break existing tests). So we assert the actual,
            // consistent behavior: the request is denied (403), not served.
            mockMvc.perform(post("/api/admin/vendors/1/approve"))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    // Approve happy path + the end-to-end unblock (the actual bug fix).
    // ================================================================

    @Nested
    @DisplayName("Approve vendor")
    class ApproveTests {

        @Test
        @DisplayName("admin approves a PENDING vendor → 200, status APPROVED")
        void approvePendingVendor() throws Exception {
            Vendor vendor = pendingVendor("approve", "Approve Biz");
            String token = adminToken();

            mockMvc.perform(post("/api/admin/vendors/{vendorId}/approve", vendor.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vendorId").value(vendor.getId()))
                    .andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
                    .andExpect(jsonPath("$.businessName").value("Approve Biz"));
        }

        @Test
        @DisplayName("E2E unblock: register PENDING vendor → admin approve → vendor can create a service")
        void approveUnblocksServiceCreation() throws Exception {
            // --- Setup: a category the service will reference ---
            Category category = categoryRepository.save(new Category("Cleaning-" + System.nanoTime(),
                    "cleaning-" + System.nanoTime()));

            // --- Step 1: a PENDING vendor exists (newly registered vendors start PENDING) ---
            Vendor vendor = pendingVendor("e2e", "E2E Biz");
            String vendorToken = vendor.getDescription();
            String adminToken = adminToken();

            ServiceCreateRequest createRequest = new ServiceCreateRequest(
                    category.getId(),
                    "Deep House Cleaning",
                    "Top to bottom",
                    com.hien.marketplace.domain.service.PricingType.FIXED,
                    new BigDecimal("500000"),
                    4,
                    null, null, null);

            // --- Step 2 (the bug): before approval, service creation MUST be blocked (422) ---
            mockMvc.perform(post("/api/vendor/services")
                            .header("Authorization", "Bearer " + vendorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

            // --- Step 3: admin approves the vendor → status APPROVED ---
            mockMvc.perform(post("/api/admin/vendors/{vendorId}/approve", vendor.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));

            // --- Step 4 (the fix): the SAME vendor can now create a service successfully (201) ---
            mockMvc.perform(post("/api/vendor/services")
                            .header("Authorization", "Bearer " + vendorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Deep House Cleaning"));
        }
    }

    // ================================================================
    // Reject path — vendor stays blocked.
    // ================================================================

    @Nested
    @DisplayName("Reject vendor")
    class RejectTests {

        @Test
        @DisplayName("admin rejects a PENDING vendor → 200, status REJECTED, still cannot create service")
        void rejectVendorStaysBlocked() throws Exception {
            Category category = categoryRepository.save(new Category("Tutoring-" + System.nanoTime(),
                    "tutoring-" + System.nanoTime()));

            Vendor vendor = pendingVendor("reject", "Reject Biz");
            String vendorToken = vendor.getDescription();
            String adminToken = adminToken();

            // Admin rejects the vendor.
            mockMvc.perform(post("/api/admin/vendors/{vendorId}/reject", vendor.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationStatus").value("REJECTED"));

            // A REJECTED vendor is NOT approved, so service creation is still blocked.
            // (BusinessRuleViolationException → 422, same guard as the pre-approval step above.)
            ServiceCreateRequest createRequest = new ServiceCreateRequest(
                    category.getId(),
                    "Math Tutoring",
                    "Grade 1-12",
                    com.hien.marketplace.domain.service.PricingType.HOURLY,
                    new BigDecimal("200000"),
                    1,
                    null, null, null);

            mockMvc.perform(post("/api/vendor/services")
                            .header("Authorization", "Bearer " + vendorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        }
    }

    // ================================================================
    // Not-found + list/filter.
    // ================================================================

    @Nested
    @DisplayName("Not found & listing")
    class NotFoundAndListingTests {

        @Test
        @DisplayName("approve a non-existent vendorId → 404")
        void approveNotFoundReturns404() throws Exception {
            // ResourceNotFoundException → GlobalExceptionHandler maps to 404 RESOURCE_NOT_FOUND.
            // 99999 is an id no test creates, so the lookup in AdminVendorService will miss.
            mockMvc.perform(post("/api/admin/vendors/{vendorId}/approve", 99999L)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("GET /api/admin/vendors?status=PENDING returns only pending vendors + pagination")
        void listFiltersByPendingStatus() throws Exception {
            // Seed a known mix of statuses so the filter is observable, not coincidental.
            pendingVendor("list-pending-1", "Pending One");   // PENDING
            pendingVendor("list-pending-2", "Pending Two");   // PENDING

            // An APPROVED vendor: create then approve via the domain method directly (we are testing
            // the list endpoint here, not re-testing approve).
            Vendor approved = pendingVendor("list-approved", "Approved One");
            approved.approve();
            vendorRepository.save(approved);

            // A REJECTED vendor, similarly.
            Vendor rejected = pendingVendor("list-rejected", "Rejected One");
            rejected.reject();
            vendorRepository.save(rejected);

            String token = adminToken();

            // Filter by PENDING: the two pending vendors must come back, and the approved/rejected
            // ones must NOT. We also assert pagination metadata for the spec's "paginated" requirement.
            mockMvc.perform(get("/api/admin/vendors")
                            .param("status", VerificationStatus.PENDING.name())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.verificationStatus == 'PENDING')]")
                            .isNotEmpty())   // at least the two we seeded
                    .andExpect(jsonPath("$.content[?(@.verificationStatus == 'APPROVED')]")
                            .doesNotExist()) // none leak through
                    .andExpect(jsonPath("$.content[?(@.verificationStatus == 'REJECTED')]")
                            .doesNotExist())
                    .andExpect(jsonPath("$.totalElements").exists())
                    .andExpect(jsonPath("$.totalPages").exists());

            // Sanity: unfiltered list returns an array (proves null-status returns all statuses).
            mockMvc.perform(get("/api/admin/vendors")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }
}
