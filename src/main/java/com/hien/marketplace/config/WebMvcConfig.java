package com.hien.marketplace.config;

import com.hien.marketplace.interfaces.rest.ValidatingPageableResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration for customizing Spring MVC behavior.
 *
 * WHY: Configure pagination validation and argument resolvers.
 * - Enforce minimum page size (prevent size=0 or negative)
 * - Enforce maximum page size (prevent excessively large queries)
 * - Configure page index handling (zero-based by default)
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Customize Pageable argument resolver for pagination validation.
     *
     * WHY: By default, Spring Data accepts any page/size values.
     * We use ValidatingPageableResolver to enforce validation:
     * - Page index must be >= 0 (zero-based)
     * - Page size must be >= 1 (positive)
     * - Page size must be <= 100 (performance limit)
     *
     * Invalid values result in 400 Bad Request with clear error message.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        ValidatingPageableResolver pageableResolver = new ValidatingPageableResolver();

        // Set fallback page size when not specified
        // WHY: Ensures consistent default behavior across all endpoints
        pageableResolver.setFallbackPageable(PageRequest.of(0, 20));

        // Enforce maximum page size to prevent performance issues
        // WHY: size=10000 could load millions of rows, crashing the app
        // Max 100 items per page is a reasonable limit for most use cases
        pageableResolver.setMaxPageSize(100);

        // Page index is zero-based by default (Spring Data standard)
        // This is the expected behavior for REST APIs
        pageableResolver.setOneIndexedParameters(false);

        // Page parameter name (default is "page")
        pageableResolver.setPageParameterName("page");

        // Size parameter name (default is "size")
        pageableResolver.setSizeParameterName("size");

        resolvers.add(pageableResolver);
    }
}
