package com.hien.marketplace.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration.
 *
 * WHY: Provides interactive API documentation at /swagger-ui.html
 * - Developers can test endpoints directly in browser
 * - Documents all request/response schemas
 * - Shows authentication requirements
 *
 * Security scheme: Bearer JWT token
 * - Click "Authorize" button in Swagger UI
 * - Paste JWT token (without "Bearer " prefix)
 * - All authenticated endpoints will include the token
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceMarketplaceOpenAPI() {
        // Define security scheme for JWT Bearer token
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter JWT token (without 'Bearer ' prefix)");

        // Apply security globally to all endpoints (can be overridden per-endpoint)
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(securityRequirement)
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme));
    }

    private Info apiInfo() {
        return new Info()
                .title("Service Marketplace API")
                .description("""
                        REST API for Service Marketplace - a platform connecting service providers with customers.

                        ## Authentication
                        - Use `/api/auth/register` to create an account
                        - Use `/api/auth/login` to get JWT tokens
                        - Include `Authorization: Bearer <token>` header for authenticated endpoints

                        ## Roles
                        - **CUSTOMER**: Browse services, create bookings, leave reviews
                        - **VENDOR**: Manage services, view bookings, track earnings
                        - **ADMIN**: Full system access
                        """)
                .version("0.0.1")
                .contact(new Contact()
                        .name("Hien Nguyen")
                        .email("fangrixian.nguyenn@outlook.com"))
                .license(new License()
                        .name("MIT")
                        .url("https://opensource.org/licenses/MIT"));
    }
}