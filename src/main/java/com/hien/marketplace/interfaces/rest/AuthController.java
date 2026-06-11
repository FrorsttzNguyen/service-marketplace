package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.AuthService;
import com.hien.marketplace.interfaces.dto.request.LoginRequest;
import com.hien.marketplace.interfaces.dto.request.RefreshTokenRequest;
import com.hien.marketplace.interfaces.dto.request.RegisterRequest;
import com.hien.marketplace.interfaces.dto.response.AuthResponse;
import com.hien.marketplace.interfaces.dto.response.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations.
 *
 * WHY: Exposes auth endpoints for public access (no authentication required).
 * - POST /api/auth/register - Create new user account
 * - POST /api/auth/login - Authenticate and get JWT tokens
 * - POST /api/auth/refresh - Refresh access token
 *
 * All endpoints are PUBLIC (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, and token refresh")
public class AuthController {

    private final AuthService authService;

    /**
     * Register new user account.
     *
     * Returns:
     * - 201 Created with JWT tokens on success
     * - 409 Conflict if email already registered
     * - 400 Bad Request if validation fails
     */
    @PostMapping("/register")
    @Operation(
            summary = "Register new user",
            description = "Create a new user account. Optionally register as vendor.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User registered successfully"),
                    @ApiResponse(responseCode = "409", description = "Email already registered"),
                    @ApiResponse(responseCode = "400", description = "Invalid input data")
            }
    )
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Login with email and password.
     *
     * Returns:
     * - 200 OK with JWT tokens on success
     * - 401 Unauthorized if credentials invalid
     * - 400 Bad Request if validation fails
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login",
            description = "Authenticate with email and password to get JWT tokens",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials"),
                    @ApiResponse(responseCode = "400", description = "Invalid input data")
            }
    )
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token.
     *
     * Returns:
     * - 200 OK with new access token
     * - 401 Unauthorized if refresh token invalid or expired
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Get a new access token using a valid refresh token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
                    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
            }
    )
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenRefreshResponse response = authService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }
}