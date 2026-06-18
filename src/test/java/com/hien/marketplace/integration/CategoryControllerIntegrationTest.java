package com.hien.marketplace.integration;

import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.infrastructure.persistence.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the public categories endpoint.
 *
 * NOTE: the test profile uses Hibernate ddl-auto=create-drop (Flyway is disabled in tests),
 * so the V11 seed does NOT run here — the categories table starts empty. We seed a row via
 * the repository so the assertion is deterministic regardless of migration seeding.
 * No auth is sent — the endpoint must be public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void seed() {
        if (categoryRepository.findBySlug("test-cleaning").isEmpty()) {
            categoryRepository.save(new Category("Test Cleaning", "test-cleaning"));
        }
    }

    @Test
    @DisplayName("GET /api/categories is public and returns categories as a JSON array")
    void listCategories_isPublic_returnsArray() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].slug").value(hasItem("test-cleaning")))
                .andExpect(jsonPath("$[*].name").value(hasItem("Test Cleaning")));
    }
}
