package com.roms.mapper;

import com.roms.dto.response.TaxCategoryResponse;
import com.roms.entity.TaxCategory;
import org.springframework.stereotype.Component;

@Component
public class TaxCategoryMapper {

    public TaxCategoryResponse toResponse(TaxCategory taxCategory) {
        if (taxCategory == null) {
            return null;
        }
        return TaxCategoryResponse.builder()
                .id(taxCategory.getId())
                .rate(taxCategory.getRate())
                .createdAt(taxCategory.getCreatedAt())
                .build();
    }
}

