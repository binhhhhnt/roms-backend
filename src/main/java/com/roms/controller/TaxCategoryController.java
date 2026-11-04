package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.TaxCategoryRequest;
import com.roms.dto.response.TaxCategoryResponse;
import com.roms.service.TaxCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tax-categories")
@RequiredArgsConstructor
public class TaxCategoryController {

    private final TaxCategoryService taxCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxCategoryResponse>>> getAllTaxCategories() {
        List<TaxCategoryResponse> list = taxCategoryService.getAllTaxCategories();
        return ResponseEntity.ok(ApiResponse.success("Tax categories retrieved successfully", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxCategoryResponse>> getTaxCategory(@PathVariable Long id) {
        TaxCategoryResponse response = taxCategoryService.getTaxCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Tax category retrieved successfully", response));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<TaxCategoryResponse>> createTaxCategory(
            @Valid @RequestBody TaxCategoryRequest request) {
        TaxCategoryResponse response = taxCategoryService.createTaxCategory(request);
        return ResponseEntity.created(URI.create("/api/tax-categories/" + response.getId()))
                .body(ApiResponse.success("Tax category created successfully", response));
    }
}

