package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.response.CategoryResponse;
import com.roms.dto.request.CategoryWriteRequest;
import org.springframework.data.domain.Pageable;

public interface CategoryService {
    PageResponse<CategoryResponse> getCategories(Pageable pageable);
    CategoryResponse getCategory(Long id);
    CategoryResponse createCategory(CategoryWriteRequest request);
    CategoryResponse updateCategory(Long id, CategoryWriteRequest request);
}
