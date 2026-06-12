package com.hien.marketplace.integration;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for JPA slice tests using TestContainers.
 *
 * WHY separate from BaseIntegrationTest?
 * - @DataJpaTest is a SLICE test (only JPA components, not full Spring context)
 * - Much faster than @SpringBootTest
 * - No web layer, no security, no custom beans
 * - Perfect for testing repositories and entity mappings
 *
 * WHY TestContainers?
 * - Even for slice tests, we want real PostgreSQL
 * - Entity mapping issues (constraint names, column types) are caught early
 * - Optimistic locking behavior tested correctly
 *
 * USAGE:
 * <pre>
 * {@code
 * @DataJpaTest
 * class MyRepositoryTest extends BaseDataJpaTest {
 *     @Autowired
 *     private TestEntityManager entityManager;
 *
 *     @Autowired
 *     private MyRepository repository;
 *
 *     @Test
 *     void testSomething() {
 *         // Test with real PostgreSQL database
 *     }
 * }
 * }
 * </pre>
 *
 * NOTE: Subclasses must annotate with @DataJpaTest
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseDataJpaTest {

    /**
     * PostgreSQL container for JPA slice tests.
     *
     * Shared across all repository tests for performance.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("marketplace_jpa_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Configure Spring to use TestContainers database.
     *
     * For @DataJpaTest, we also need to:
     * - Enable Flyway (it's disabled by default in slice tests)
     * - Use validate mode (Flyway manages schema, Hibernate validates)
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Enable Flyway for JPA slice tests
        registry.add("spring.flyway.enabled", () -> "true");
        // Hibernate should validate schema, not create it
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}