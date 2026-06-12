package com.hien.marketplace.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using TestContainers.
 *
 * WHY TestContainers instead of H2?
 * 1. Production parity: Tests run against real PostgreSQL, not H2
 * 2. Database-specific behavior:
 *    - Optimistic locking behavior matches production
 *    - Constraint violations are identical
 *    - Transaction isolation levels are real
 * 3. Flyway migrations tested: Real migrations run against real DB
 * 4. Catch production-only bugs early
 *
 * HOW IT WORKS:
 * 1. @Testcontainers: JUnit 5 extension that manages container lifecycle
 * 2. @Container: Static field creates ONE container for ALL tests (performance)
 * 3. @DynamicPropertySource: Overrides Spring properties with container connection info
 * 4. @SpringBootTest: Full Spring context with real database
 *
 * PERFORMANCE:
 * - Container starts ONCE per test suite (static field)
 * - Tests reuse the same container
 * - Container is automatically stopped after all tests complete
 * - First run: ~10s to pull PostgreSQL image
 * - Subsequent runs: ~2s to start container
 *
 * USAGE:
 * <pre>
 * {@code
 * @SpringBootTest
 * class MyIntegrationTest extends BaseIntegrationTest {
 *     @Test
 *     void testSomething() {
 *         // Test with real PostgreSQL database
 *     }
 * }
 * }
 * </pre>
 *
 * REQUIREMENTS:
 * - Docker must be running
 * - First run needs internet to pull postgres:15-alpine image
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    /**
     * PostgreSQL container for integration tests.
     *
     * WHY static?
     * - Shared across ALL test classes that extend BaseIntegrationTest
     * - Container starts ONCE before any tests run
     * - Container stops ONCE after all tests complete
     * - Massive performance improvement vs per-class containers
     *
     * WHY postgres:15-alpine?
     * - Alpine images are smaller (~80MB vs ~400MB)
     * - PostgreSQL 15 is stable and production-ready
     * - Matches common production deployment
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("marketplace_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Configure Spring to use TestContainers database.
     *
     * This method runs BEFORE Spring context starts.
     * It overrides the datasource properties in application-test.yml
     * with the dynamic connection info from the running container.
     *
     * WHY DynamicPropertySource?
     * - Container port is assigned dynamically (not fixed)
     * - We can't hardcode connection URL in application-test.yml
     * - Spring calls this method and injects properties before context loads
     *
     * OVERRIDES:
     * - spring.datasource.url → container's JDBC URL
     * - spring.datasource.username → container's username
     * - spring.datasource.password → container's password
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Override driver to use PostgreSQL (not H2)
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Enable Flyway for integration tests (run real migrations)
        registry.add("spring.flyway.enabled", () -> "true");
        // Don't use ddl-auto, let Flyway manage schema
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
