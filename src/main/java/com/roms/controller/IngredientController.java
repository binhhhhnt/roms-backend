package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.CreateIngredientRequest;
import com.roms.dto.request.UpdateIngredientRequest;
import com.roms.dto.response.ExcelImportResultResponse;
import com.roms.dto.response.IngredientResponse;
import com.roms.service.IngredientExcelService;
import com.roms.service.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;
    private final IngredientExcelService excelService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF', 'KITCHEN_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<IngredientResponse>>> getIngredients(
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        var page = ingredientService.getIngredients(isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF', 'KITCHEN_STAFF')")
    public ResponseEntity<ApiResponse<IngredientResponse>> getIngredientById(@PathVariable Long id) {
        var response = ingredientService.getIngredientById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<IngredientResponse>> createIngredient(
            @Valid @RequestBody CreateIngredientRequest request) {
        var response = ingredientService.createIngredient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ingredient created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<IngredientResponse>> updateIngredient(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIngredientRequest request) {
        var response = ingredientService.updateIngredient(id, request);
        return ResponseEntity.ok(ApiResponse.success("Ingredient updated successfully", response));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<IngredientResponse>> deactivateIngredient(@PathVariable Long id) {
        var response = ingredientService.deactivateIngredient(id);
        return ResponseEntity.ok(ApiResponse.success("Ingredient deactivated successfully", response));
    }

    @GetMapping("/excel/template")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = excelService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ingredients_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/excel/export")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<byte[]> exportIngredients() {
        byte[] content = excelService.exportIngredients();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ingredients_export.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/excel/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<ExcelImportResultResponse>> importIngredients(@RequestParam("file") MultipartFile file) {
        var result = excelService.importIngredients(file);
        return ResponseEntity.ok(ApiResponse.success("Import ingredients completed", result));
    }
}
