package com.hien.marketplace.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Custom Pageable argument resolver with validation.
 *
 * WHY: Spring Data's default PageableHandlerMethodArgumentResolver doesn't
 * validate page/size values - it just uses fallbacks for invalid values.
 * This resolver enforces:
 * - Non-negative page numbers (page >= 0)
 * - Positive page sizes (size > 0)
 * - Maximum page size limit (size <= 100)
 *
 * RETURNS: 400 Bad Request for invalid pagination parameters.
 */
public class ValidatingPageableResolver extends PageableHandlerMethodArgumentResolver {

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @NonNull
    public Pageable resolveArgument(
            @NonNull MethodParameter methodParameter,
            @Nullable ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory
    ) {
        // Validate raw request parameters BEFORE resolving
        // WHY: Spring Data's resolver normalizes values, so we need to check
        // the raw values from the HTTP request first
        validateRawParameters(webRequest);

        // Resolve the Pageable from the request
        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);

        // Additional validation on resolved Pageable (belt and suspenders)
        if (pageable.getPageNumber() < 0) {
            throw new InvalidPaginationParameterException("Page index must not be negative");
        }

        if (pageable.getPageSize() < 1) {
            throw new InvalidPaginationParameterException("Page size must be at least 1");
        }

        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidPaginationParameterException("Page size must not exceed " + MAX_PAGE_SIZE);
        }

        return pageable;
    }

    /**
     * Validate raw pagination parameters from HTTP request.
     *
     * WHY: Spring Data's resolver normalizes negative/zero values to defaults
     * before we can check them. We need to validate the raw string values first.
     */
    private void validateRawParameters(NativeWebRequest webRequest) {
        // Get the native HTTP servlet request to access raw parameters
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        if (servletRequest == null) {
            return;
        }

        // Get page parameter
        String pageParam = servletRequest.getParameter("page");
        if (pageParam != null && !pageParam.isBlank()) {
            try {
                int page = Integer.parseInt(pageParam);
                if (page < 0) {
                    throw new InvalidPaginationParameterException("Page index must not be negative");
                }
            } catch (NumberFormatException e) {
                throw new InvalidPaginationParameterException("Page index must be a valid integer");
            }
        }

        // Get size parameter
        String sizeParam = servletRequest.getParameter("size");
        if (sizeParam != null && !sizeParam.isBlank()) {
            try {
                int size = Integer.parseInt(sizeParam);
                if (size < 1) {
                    throw new InvalidPaginationParameterException("Page size must be at least 1");
                }
                if (size > MAX_PAGE_SIZE) {
                    throw new InvalidPaginationParameterException("Page size must not exceed " + MAX_PAGE_SIZE);
                }
            } catch (NumberFormatException e) {
                throw new InvalidPaginationParameterException("Page size must be a valid integer");
            }
        }
    }
}
