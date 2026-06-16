package com.hien.marketplace.config;

import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds an initial ADMIN user from environment variables (Phase 5.5).
 *
 * WHY we need this:
 * The new {@code /api/admin/**} endpoints require {@code ROLE_ADMIN}. Without at least one admin
 * account, no one can ever approve a vendor, and the vendor onboarding bug would reappear in
 * practice (vendors stuck PENDING). This runner ensures an admin exists when the app starts.
 *
 * WHY env vars, not a Flyway migration:
 * A migration is a committed SQL file — any admin password baked into it leaks into source control
 * (a credential leak that review must reject). Env vars keep the secret OUT of the repo, follow the
 * 12-factor app config model, and let each deployment (dev/staging/prod) use a different credential.
 *
 * WHEN IT RUNS:
 * Only if BOTH {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD} are set (non-blank). If either is
 * missing, the runner logs once and does nothing — the app still starts fine (admin can be created
 * another way in real deployments). This makes the bean safe for local/test where no admin is wanted.
 *
 * IDEMPOTENT:
 * If an admin with that email already exists, we skip — restart-safe. We never overwrite an existing
 * admin's password on boot.
 *
 * NOTE FOR TESTS:
 * The integration tests do NOT rely on this runner; they create the admin user directly in the test
 * setup (deterministic, no env dependency) — see AdminControllerIntegrationTest.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    // @Value with a default of "" means missing env → blank, which we treat as "not configured".
    // We intentionally do NOT put these in application.yml defaults (no default admin password).
    @Value("${admin.email:}")
    private String configuredEmail;

    @Value("${admin.password:}")
    private String configuredPassword;

    @Override
    public void run(String... args) {
        // Prefer the relaxed-bound Spring properties (admin.email/admin.password), but also honor
        // the raw ADMIN_EMAIL / ADMIN_PASSWORD env vars so operators can set them without Spring's
        // binding rules. This keeps 12-factor env injection working exactly as the spec requires.
        String email = firstNonBlank(configuredEmail, environment.getProperty("ADMIN_EMAIL"));
        String password = firstNonBlank(configuredPassword, environment.getProperty("ADMIN_PASSWORD"));

        // Only seed when BOTH are explicitly configured. Otherwise no-op (don't guess credentials).
        if (isBlank(email) || isBlank(password)) {
            log.info("AdminBootstrap: ADMIN_EMAIL/ADMIN_PASSWORD not set — skipping admin seeding.");
            return;
        }

        // Idempotency: if the admin already exists, never touch it.
        if (userRepository.existsByEmail(email)) {
            log.info("AdminBootstrap: admin user '{}' already exists — skipping.", email);
            return;
        }

        // Hash the password with the same PasswordEncoder used everywhere else (BCrypt).
        // We never store plaintext.
        User admin = new User(
                email,
                passwordEncoder.encode(password),
                "Administrator",
                UserRole.ADMIN
        );
        userRepository.save(admin);
        log.info("AdminBootstrap: seeded admin user '{}'.", email);
    }

    /** Return the first argument that is non-blank, else null. */
    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a;
        if (!isBlank(b)) return b;
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
