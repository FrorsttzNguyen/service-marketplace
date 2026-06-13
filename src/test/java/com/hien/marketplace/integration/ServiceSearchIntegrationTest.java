package com.hien.marketplace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.*;
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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Service search endpoint.
 *
 * WHY: Test the full search flow with real database queries.
 * - Tests Specification-based dynamic filtering
 * - Tests pagination
 * - Tests that inactive services are never returned
 *
 * Test data setup:
 * - 1 vendor user with vendor profile
 * - 1 category ("Haircut")
 * - 3 services with varying attributes:
 *   - Service A: Premium Haircut, $100, hanoi, ACTIVE
 *   - Service B: Basic Massage, $50, saigon, ACTIVE
 *   - Service C: VIP Haircut, $200, hanoi, INACTIVE
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ServiceSearchIntegrationTest {

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
    private ServiceRepository serviceRepository;

    private Category category;
    private ServiceEntity serviceA;
    private ServiceEntity serviceB;
    private ServiceEntity serviceC;

    @BeforeEach
    void setUp() {
        // Create vendor user
        User vendorUser = new User("vendor-search@test.com", "hashedPassword", "Vendor Search", UserRole.VENDOR);
        vendorUser = userRepository.save(vendorUser);

        // Create vendor profile with address (city is denormalized from vendor address)
        Vendor vendor = new Vendor(vendorUser, "Test Vendor Business");
        vendor.setAddress(new Address("123 Street", "hanoi", "10000")); // Default city for vendor
        vendor = vendorRepository.save(vendor);

        // Create category (requires name and slug)
        category = new Category("Haircut", "haircut");
        category = categoryRepository.save(category);

        // Service A: Premium Haircut, $100 (10000 cents), hanoi, ACTIVE
        // City is denormalized from vendor address when service is created
        serviceA = new ServiceEntity(vendor, "Premium Haircut", Money.of(10000), PricingType.FIXED, 60);
        serviceA.setCategory(category);
        serviceA.updateCity("hanoi");
        serviceA.activate();
        serviceA = serviceRepository.save(serviceA);

        // Service B: Basic Massage, $50 (5000 cents), saigon, ACTIVE
        serviceB = new ServiceEntity(vendor, "Basic Massage", Money.of(5000), PricingType.FIXED, 60);
        serviceB.setCategory(category);
        serviceB.updateCity("saigon");
        serviceB.activate();
        serviceB = serviceRepository.save(serviceB);

        // Service C: VIP Haircut, $200 (20000 cents), hanoi, INACTIVE
        serviceC = new ServiceEntity(vendor, "VIP Haircut", Money.of(20000), PricingType.FIXED, 60);
        serviceC.setCategory(category);
        serviceC.updateCity("hanoi");
        // NOT activated - stays INACTIVE
        serviceC = serviceRepository.save(serviceC);
    }

    @Nested
    @DisplayName("Search Services")
    class SearchServices {

        @Test
        @DisplayName("Search with no filters returns active services only")
        void searchWithNoFilters_returnsActiveOnly() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[?(@.title == 'Premium Haircut')]").exists())
                    .andExpect(jsonPath("$.content[?(@.title == 'Basic Massage')]").exists())
                    .andExpect(jsonPath("$.content[?(@.title == 'VIP Haircut')]").doesNotExist());
        }

        @Test
        @DisplayName("Search by keyword matches name case insensitive")
        void searchByKeyword_matchesNameCaseInsensitive() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("keyword", "haircut")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("Premium Haircut"));
        }

        @Test
        @DisplayName("Search by category filters correctly")
        void searchByCategory_filtersCorrectly() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("categoryId", category.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[?(@.title == 'Premium Haircut')]").exists())
                    .andExpect(jsonPath("$.content[?(@.title == 'Basic Massage')]").exists());
        }

        @Test
        @DisplayName("Search by city exact match")
        void searchByCity_exactMatch() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("city", "hanoi")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("Premium Haircut"));
        }

        @Test
        @DisplayName("Search by price range filters in cents")
        void searchByPriceRange_filtersCents() throws Exception {
            // minPrice=60, maxPrice=150 means 6000-15000 cents
            // Service A: 10000 cents ($100) - should match
            // Service B: 5000 cents ($50) - should NOT match
            mockMvc.perform(get("/api/services/search")
                            .param("minPrice", "60")
                            .param("maxPrice", "150")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("Premium Haircut"));
        }

        @Test
        @DisplayName("Search by keyword with no match returns empty")
        void searchByKeywordNoMatch_returnsEmpty() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("keyword", "nonexistent")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Search with combined filters")
        void searchCombinedFilters() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("keyword", "haircut")
                            .param("city", "hanoi")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("Premium Haircut"));
        }
    }

    // ================================================================
    // Pagination Behavior Tests
    // ================================================================

    @Nested
    @DisplayName("Pagination Behavior")
    class PaginationTests {

        @Test
        @DisplayName("Page index is zero-based - page=0 returns first page")
        void pageIndex_isZeroBased() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("page", "0")
                            .param("size", "1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.number").value(0))  // Current page number
                    .andExpect(jsonPath("$.first").value(true));
        }

        @Test
        @DisplayName("Page=1 returns second page (zero-based indexing)")
        void pageOne_returnsSecondPage() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("page", "1")
                            .param("size", "1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.number").value(1))
                    .andExpect(jsonPath("$.first").value(false));
        }

        @Test
        @DisplayName("Negative page number should be rejected with 400 Bad Request")
        void negativePage_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("page", "-1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Page size must be positive - size=0 should be rejected")
        void zeroPageSize_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("page", "0")
                            .param("size", "0")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Negative page size should be rejected")
        void negativePageSize_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("page", "0")
                            .param("size", "-5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Large page size should be rejected")
        void largePageSize_shouldBeRejected() throws Exception {
            // Test with excessively large page size (1000)
            // API rejects page sizes > 100 to prevent performance issues
            mockMvc.perform(get("/api/services/search")
                            .param("page", "0")
                            .param("size", "1000")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGINATION_PARAMETER"));
        }

        @Test
        @DisplayName("Default page size is 20")
        void defaultPageSize_is20() throws Exception {
            // Create 25 more services to test default pagination
            for (int i = 0; i < 25; i++) {
                ServiceEntity svc = new ServiceEntity(
                        serviceA.getVendor(),
                        "Test Service " + i,
                        Money.of(1000),
                        PricingType.FIXED,
                        60
                );
                svc.setCategory(category);
                svc.activate();
                serviceRepository.save(svc);
            }

            mockMvc.perform(get("/api/services/search")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(20)))  // Default size
                    .andExpect(jsonPath("$.size").value(20));
        }
    }

    // ================================================================
    // Sorting Behavior Tests
    // ================================================================

    @Nested
    @DisplayName("Sorting Behavior")
    class SortingTests {

        @Test
        @DisplayName("Sort by valid field works correctly")
        void sortByValidField_worksCorrectly() throws Exception {
            // Sort by basePrice ascending
            mockMvc.perform(get("/api/services/search")
                            .param("sort", "basePrice.amountCents,asc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].title").value("Basic Massage"))  // $50
                    .andExpect(jsonPath("$.content[1].title").value("Premium Haircut"));  // $100
        }

        @Test
        @DisplayName("Sort by descending order works")
        void sortByDescendingOrder_works() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("sort", "basePrice.amountCents,desc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].title").value("Premium Haircut"))
                    .andExpect(jsonPath("$.content[1].title").value("Basic Massage"));
        }

        @Test
        @DisplayName("Invalid sort field should not cause 500 error")
        void invalidSortField_shouldNotCause500() throws Exception {
            mockMvc.perform(get("/api/services/search")
                            .param("sort", "nonexistentField,asc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());  // Should return 400, not 500
        }

        @Test
        @DisplayName("Deterministic sort appends id tie-breaker")
        void deterministicSort_appendsIdTieBreaker() throws Exception {
            // Create two services with the same price as serviceA.
            // WHY: The primary sort value ties, so id ASC must decide the order.
            ServiceEntity svc1 = new ServiceEntity(
                    serviceA.getVendor(),
                    "Service Same Price A",
                    Money.of(10000),
                    PricingType.FIXED,
                    60
            );
            svc1.setCategory(category);
            svc1.activate();
            svc1 = serviceRepository.save(svc1);

            ServiceEntity svc2 = new ServiceEntity(
                    serviceA.getVendor(),
                    "Service Same Price B",
                    Money.of(10000),
                    PricingType.FIXED,
                    60
            );
            svc2.setCategory(category);
            svc2.activate();
            svc2 = serviceRepository.save(svc2);

            mockMvc.perform(get("/api/services/search")
                            .param("sort", "basePrice.amountCents,asc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(4)))
                    .andExpect(jsonPath("$.content[0].title").value("Basic Massage"))
                    .andExpect(jsonPath("$.content[1].id").value(serviceA.getId().intValue()))
                    .andExpect(jsonPath("$.content[2].id").value(svc1.getId().intValue()))
                    .andExpect(jsonPath("$.content[3].id").value(svc2.getId().intValue()));
        }

        @Test
        @DisplayName("Multiple sort parameters work")
        void multipleSortParameters_work() throws Exception {
            // Sort by price then by createdAt
            mockMvc.perform(get("/api/services/search")
                            .param("sort", "basePrice.amountCents,asc")
                            .param("sort", "createdAt,desc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }
}
