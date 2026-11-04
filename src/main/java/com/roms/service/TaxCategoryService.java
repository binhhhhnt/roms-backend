package com.roms.service;

import com.roms.dto.request.TaxCategoryRequest;
import com.roms.dto.response.TaxCategoryResponse;
import com.roms.entity.TaxCategory;

import java.math.BigDecimal;
import java.util.List;

public interface TaxCategoryService {

    List<TaxCategoryResponse> getAllTaxCategories();

    TaxCategoryResponse getTaxCategory(Long id);

    TaxCategoryResponse createTaxCategory(TaxCategoryRequest request);

    TaxCategory findOrCreateByRate(BigDecimal rate);
}

