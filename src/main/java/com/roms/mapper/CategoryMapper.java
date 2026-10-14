package com.roms.mapper;

import com.roms.dto.response.CategoryResponse;
import com.roms.dto.request.CategoryWriteRequest;
import com.roms.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    public void updateEntity(CategoryWriteRequest request, Category category) {
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());
        category.setDisplayOrder(request.getDisplayOrder());
        category.setIsActive(request.getIsActive());
    }
    public CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .build();
    }
}
