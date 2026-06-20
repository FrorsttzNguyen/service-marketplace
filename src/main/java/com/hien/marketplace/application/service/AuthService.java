package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.AuthenticationException;
import com.hien.marketplace.interfaces.dto.response.UserResponse;
import com.hien.marketplace.application.exception.DuplicateResourceException;
import com.hien.marketplace.application.mapper.UserMapper;
import com.hien.marketplace.domain.common.PhoneNumber;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.user.UserStatus;
import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.infrastructure.persistence.UserRepository;
import com.hien.marketplace.infrastructure.persistence.ProviderRepository;
import com.hien.marketplace.infrastructure.security.CustomUserDetailsService;
import com.hien.marketplace.infrastructure.security.JwtUtils;
import com.hien.marketplace.interfaces.dto.request.LoginRequest;
import com.hien.marketplace.interfaces.dto.request.RegisterRequest;
import com.hien.marketplace.interfaces.dto.response.AuthResponse;
import com.hien.marketplace.interfaces.dto.response.TokenRefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for authentication operations: register, login, refresh token.
 *
 * WHY: Authentication is a use case (application layer), not domain logic.
 * - Domain entities handle password hashing, role assignment
 * - Service orchestrates: validate input → create user → generate JWT
 *
 * Transaction boundaries:
 * - @Transactional ensures all DB operations succeed or fail together
 * - register: user creation + provider profile creation (if applicable)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Register new user.
     *
     * Flow:
     * 1. Check email not already registered
     * 2. Create User entity with hashed password
     * 3. Save to database
     * 4. If registerAsProvider=true, create Provider profile
     * 5. Generate JWT tokens
     * 6. Return AuthResponse with tokens + user info
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Step 1: Check duplicate email
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        // Step 2: Create User entity
        String hashedPassword = passwordEncoder.encode(request.password());
        UserRole role = Boolean.TRUE.equals(request.registerAsProvider())
                ? UserRole.VENDOR
                : UserRole.CUSTOMER;

        User user = new User(
                request.email(),
                hashedPassword,
                request.fullName(),
                role
        );
        user.changePhone(new PhoneNumber(request.phoneNumber()));

        // Step 3: Save to database
        user = userRepository.save(user);

        // Step 4: Create Provider profile if registerAsProvider=true
        if (Boolean.TRUE.equals(request.registerAsProvider())) {
            // Provider business name defaults to user's full name
            // User can update later via ProviderProfileRequest
            Provider provider = new Provider(user, request.fullName() + "'s Services");
            providerRepository.save(provider);
        }

        // Step 5: Generate JWT tokens
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());

        // Step 6: Return response
        return new AuthResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                accessToken,
                refreshToken,
                jwtUtils.getAccessTokenExpiration(),
                jwtUtils.getRefreshTokenExpiration()
        );
    }

    /**
     * Login with email + password.
     *
     * Flow:
     * 1. Find user by email
     * 2. Verify password matches hash
     * 3. Generate JWT tokens
     * 4. Return AuthResponse
     */
    public AuthResponse login(LoginRequest request) {
        // Step 1: Find user by email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        // Step 2: Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        // Step 3: Check user is active
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("Account is not active");
        }

        // Step 4: Generate JWT tokens
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());

        // Step 5: Return response
        return new AuthResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                accessToken,
                refreshToken,
                jwtUtils.getAccessTokenExpiration(),
                jwtUtils.getRefreshTokenExpiration()
        );
    }

    /**
     * Refresh access token using refresh token.
     *
     * Flow:
     * 1. Validate refresh token
     * 2. Extract userId from token
     * 3. Load user from database
     * 4. Generate new access token
     * 5. Return TokenRefreshResponse
     */
    public TokenRefreshResponse refreshToken(String refreshToken) {
        // Step 1: Validate refresh token
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new AuthenticationException("Invalid or expired refresh token");
        }

        // Step 2: Extract userId
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);

        // Step 3: Load user from database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // Step 4: Generate new access token
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        // Step 5: Return response
        return new TokenRefreshResponse(accessToken, jwtUtils.getAccessTokenExpiration());
    }

    /**
     * Return the current authenticated user's profile (the "who am I" endpoint).
     *
     * WHY: refresh only returns a new access token (no profile), so after a page reload the client
     * has a valid session but no user info (name/role). The frontend calls this on boot to rehydrate
     * the profile — and role-gated routes (provider/admin) need the role to be known after a reload.
     *
     * @param userId the authenticated user's id (from the JWT principal)
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));
        return userMapper.toResponse(user);
    }
}