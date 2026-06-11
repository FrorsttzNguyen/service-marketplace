package com.hien.marketplace.domain.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho User entity.
 * Kiểm tra creation, role assignment, và status transitions.
 */
class UserTest {

    @Test
    void shouldCreateUserWithCorrectFields() {
        User user = new User("hien@email.com", "hashed_password", "Hien Nguyen", UserRole.CUSTOMER);

        assertThat(user.getEmail()).isEqualTo("hien@email.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed_password");
        assertThat(user.getFullName()).isEqualTo("Hien Nguyen");
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldBeActiveByDefault() {
        User user = new User("test@email.com", "hash", "Test", UserRole.VENDOR);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void shouldDeactivateUser() {
        User user = new User("test@email.com", "hash", "Test", UserRole.CUSTOMER);
        user.deactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void shouldSuspendUser() {
        User user = new User("test@email.com", "hash", "Test", UserRole.CUSTOMER);
        user.suspend();

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.isActive()).isFalse();
    }
}
