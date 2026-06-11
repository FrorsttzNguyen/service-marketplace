package com.hien.marketplace.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom UserDetailsService for Spring Security authentication.
 *
 * WHY: Spring Security requires UserDetailsService interface to load user details.
 * This service is used by Spring Security during authentication process.
 *
 * HOW IT WORKS:
 * - loadUserByUsername() is called during login to validate credentials
 * - Returns UserDetails with email, password hash, and authorities (roles)
 * - Spring Security compares password hash with provided password
 *
 * Note: For JWT auth, we don't actually use password validation in filter.
 * But we still need this for initial login validation.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // In Phase 2, we'll inject UserRepository here to load user from DB
    // For now, we'll create a placeholder that will be properly implemented in Phase 3

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // TODO: Phase 3 - Load user from UserRepository.findByEmail(email)
        // For Phase 2, we return a placeholder
        // This will be properly implemented when we create AuthService

        throw new UsernameNotFoundException("User service not yet implemented - Phase 3");
    }

    /**
     * Create UserDetails from domain User entity.
     * Used by AuthService after successful login.
     */
    public UserDetails createUserDetails(
            Long userId,
            String email,
            String passwordHash,
            String role
    ) {
        return new CustomUserDetails(userId, email, passwordHash, role);
    }

    /**
     * Custom UserDetails implementation with userId field.
     * Standard UserDetails doesn't have userId, but we need it for JWT.
     */
    public static class CustomUserDetails implements UserDetails {

        private final Long userId;
        private final String email;
        private final String passwordHash;
        private final String role;

        public CustomUserDetails(Long userId, String email, String passwordHash, String role) {
            this.userId = userId;
            this.email = email;
            this.passwordHash = passwordHash;
            this.role = role;
        }

        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return email;  // Use email as username
        }

        @Override
        public String getPassword() {
            return passwordHash;
        }

        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return java.util.Collections.singletonList(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)
            );
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;  // We don't implement account expiration yet
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;  // We don't implement account locking yet
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;  // We don't implement credential expiration yet
        }

        @Override
        public boolean isEnabled() {
            return true;  // All users are enabled by default
        }
    }
}