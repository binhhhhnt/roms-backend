package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.response.CategoryResponse;
import com.roms.dto.request.CategoryWriteRequest;
import com.roms.entity.Category;
import com.roms.enums.UserRole;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.exception.ValidationException;
import com.roms.mapper.CategoryMapper;
import com.roms.repository.CategoryRepository;
import com.roms.security.SecurityUtils;
import com.roms.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "description", "displayOrder", "isActive");

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final MenuContextInvalidator invalidator;

    @Override @Transactional
    public CategoryResponse createCategory(CategoryWriteRequest request) {
        CatalogAccess.requireOwner();
        Category category = new Category();
        categoryMapper.updateEntity(request, category);
        Category saved = categoryRepository.save(category);
        invalidator.afterCommit();
        return categoryMapper.toResponse(saved);
    }

    @Override @Transactional
    public CategoryResponse updateCategory(Long id, CategoryWriteRequest request) {
        CatalogAccess.requireOwner();
        if (id == null || id <= 0) throw new ValidationException("Category id must be positive");
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        categoryMapper.updateEntity(request, category);
        Category saved = categoryRepository.save(category);
        invalidator.afterCommit();
        return categoryMapper.toResponse(saved);
    }

    @Override
    public PageResponse<CategoryResponse> getCategories(Pageable pageable) {
        UserRole role = currentRole();
        Pageable normalized = normalizePageable(pageable);
        Page<Category> categories = role == UserRole.TABLE_ACCOUNT
                ? categoryRepository.findByIsActiveTrue(normalized)
                : categoryRepository.findAll(normalized);
        return PageResponse.from(categories, categoryMapper::toResponse);
    }

    @Override
    public CategoryResponse getCategory(Long id) {
        UserRole role = currentRole();
        if (id == null || id <= 0) {
            throw new ValidationException("Category id must be positive");
        }
        Category category = categoryRepository.findById(id)
                .filter(value -> role != UserRole.TABLE_ACCOUNT || Boolean.TRUE.equals(value.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return categoryMapper.toResponse(category);
    }

    private UserRole currentRole() {
        return SecurityUtils.getCurrentUserRole()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    private Pageable normalizePageable(Pageable pageable) {
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORT_FIELDS.contains(order.getProperty())) {
                throw new ValidationException("Unsupported category sort field: " + order.getProperty());
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by("displayOrder");
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    }
}
