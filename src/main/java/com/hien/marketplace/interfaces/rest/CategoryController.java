package com.hien.marketplace.interfaces.rest;

import com.hien.marketplace.application.service.CategoryService;
import com.hien.marketplace.interfaces.dto.response.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public read endpoint for service categories.
 *
 * WHY public: categories are non-sensitive reference data needed by the (public) catalog
 * category filter and the provider "create service" form. Configured permitAll in SecurityConfig.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Service categories (public reference data)")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(
            summary = "List categories",
            description = "Return all service categories. Public — used by the catalog filter and the provider create-service form.",
            responses = @ApiResponse(responseCode = "200", description = "Categories returned")
    )
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.listAll());
    }
}
