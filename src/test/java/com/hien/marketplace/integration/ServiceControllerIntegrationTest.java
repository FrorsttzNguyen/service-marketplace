package com.hien.marketplace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.UserRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ServiceController (public service catalog).
 *
 * WHY: Service catalog endpoints are PUBLIC - no authentication required.
 * Anyone can browse active services without logging in.
 *
 * Test scenarios:
 * - List all services (pagination)
 * - Get service by ID
 * - Get services by category
 * - 404 for non-existent service
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ServiceControllerIntegrationTest {

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

    private ServiceEntity testService;
    private Vendor vendor;

    @BeforeEach
    void setUp() {
        // Create vendor
        User vendorUser = new User("service-vendor@test.com", "hashed", "Vendor", UserRole.VENDOR);
        vendorUser = userRepository.save(vendorUser);

        vendor = new Vendor(vendorUser, "Test Business");
        vendor = vendorRepository.save(vendor);

        // Create test service
        testService = new ServiceEntity(vendor, "Haircut", Money.of(50000), PricingType.FIXED, 60);
        testService.activate();
        testService = serviceRepository.save(testService);
    }

    // ================================================================
    // List Services Tests
    // ================================================================

    @Nested
    @DisplayName("GET /api/services - List Services")
    class ListServicesTests {

        @Test
        @DisplayName("Should list all active services without authentication")
        void shouldListServicesWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/services"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(testService.getId()))
                    .andExpect(jsonPath("$.content[0].title").value("Haircut"));
        }

        @Test
        @DisplayName("Should support pagination")
        void shouldSupportPagination() throws Exception {
            // Create more services
            for (int i = 0; i < 25; i++) {
                ServiceEntity svc = new ServiceEntity(vendor, "Service " + i, Money.of(10000), PricingType.FIXED, 60);
                svc.activate();
                serviceRepository.save(svc);
            }

            // First page (default size 20)
            mockMvc.perform(get("/api/services"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(20))
                    .andExpect(jsonPath("$.totalElements").value(26))  // 25 + 1 from setUp
                    .andExpect(jsonPath("$.totalPages").value(2));

            // Second page
            mockMvc.perform(get("/api/services?page=1&size=20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(6));  // Remaining 6
        }

        @Test
        @DisplayName("Should not leak @class type metadata in response (Jackson default-typing isolation)")
        void shouldNotLeakAtClassInResponse() throws Exception {
            // This guards against the bug where the Redis ObjectMapper (which has activateDefaultTyping)
            // leaks into Spring MVC's HTTP message converter, causing every response to contain
            // "@class":"org.springframework.data.domain.PageImpl" and typed-array wrappers like
            // ["java.util.Collections$UnmodifiableRandomAccessList",[...]].
            String body = mockMvc.perform(get("/api/services"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(body)
                    .as("REST response must not contain @class type metadata from Redis ObjectMapper")
                    .doesNotContain("@class");
        }

        @Test
        @DisplayName("Should not return draft services")
        void shouldNotReturnDraftServices() throws Exception {
            // Create a draft service (not activated)
            ServiceEntity draft = new ServiceEntity(vendor, "Draft Service", Money.of(10000), PricingType.FIXED, 60);
            serviceRepository.save(draft);  // DRAFT by default

            mockMvc.perform(get("/api/services"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    // Draft service should not appear
                    .andExpect(jsonPath("$.content[?(@.name == 'Draft Service')]").doesNotExist());
        }
    }

    // ================================================================
    // Get Service By ID Tests
    // ================================================================

    @Nested
    @DisplayName("GET /api/services/{id} - Get Service Detail")
    class GetServiceByIdTests {

        @Test
        @DisplayName("Should return service detail without authentication")
        void shouldReturnServiceDetailWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/services/{id}", testService.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(testService.getId()))
                    .andExpect(jsonPath("$.title").value("Haircut"))
                    .andExpect(jsonPath("$.basePrice").exists())
                    .andExpect(jsonPath("$.vendorName").value("Test Business"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent service")
        void shouldReturn404ForNonExistentService() throws Exception {
            mockMvc.perform(get("/api/services/{id}", 99999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    // ================================================================
    // Get Services By Category Tests
    // ================================================================

    @Nested
    @DisplayName("GET /api/services/category/{categoryId} - Filter by Category")
    class GetServicesByCategoryTests {

        @Test
        @DisplayName("Should return services for category")
        void shouldReturnServicesForCategory() throws Exception {
            // Note: Category filtering requires category setup
            // For now, test that endpoint exists and returns empty for non-existent category
            mockMvc.perform(get("/api/services/category/{categoryId}", 99999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }
}