package com.hien.marketplace.application.service;

import com.hien.marketplace.domain.category.Category;
import com.hien.marketplace.infrastructure.persistence.CategoryRepository;
import com.hien.marketplace.interfaces.dto.response.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Read service for service categories (reference data).
 *
 * WHY a service (not the controller hitting the repo directly): keeps the layering
 * consistent (interfaces → application → infrastructure) and gives the mapping a home.
 * Categories are a small, rarely-changing set, so a plain findAll is fine — no paging.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /** All categories, ordered by id for a stable UI ordering. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getId))
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription()))
                .toList();
    }
}
