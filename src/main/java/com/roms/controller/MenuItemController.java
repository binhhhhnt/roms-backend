package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.ExcelImportResultResponse;
import com.roms.dto.response.MenuItemReadResponse;
import com.roms.dto.response.MenuItemResponse;
import com.roms.enums.MenuItemStatus;
import com.roms.service.MenuItemExcelService;
import com.roms.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService service;
    private final MenuItemExcelService excelService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MenuItemReadResponse>>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) MenuItemStatus status,
            @RequestParam(defaultValue = "1") int quantity,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Menu items retrieved successfully", service.getMenuItems(categoryId, status, quantity, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuItemReadResponse>> detail(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int quantity) {
        return ResponseEntity.ok(ApiResponse.success("Menu item retrieved successfully", service.getMenuItem(id, quantity)));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<MenuItemResponse>> create(@Valid @RequestBody MenuItemWriteRequest request) {
        var response = service.createMenuItem(request);
        return ResponseEntity.created(URI.create("/api/menu-items/" + response.getId()))
                .body(ApiResponse.success("Menu item created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<MenuItemResponse>> update(@PathVariable Long id, @Valid @RequestBody MenuItemWriteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Menu item updated successfully", service.updateMenuItem(id, request)));
    }

    @GetMapping("/excel/template")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = excelService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=menu_items_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/excel/export")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<byte[]> exportMenuItems() {
        byte[] content = excelService.exportMenuItems();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=menu_items_export.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/excel/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ExcelImportResultResponse>> importMenuItems(@RequestParam("file") MultipartFile file) {
        var result = excelService.importMenuItems(file);
        return ResponseEntity.ok(ApiResponse.success("Import menu items completed", result));
    }
}
