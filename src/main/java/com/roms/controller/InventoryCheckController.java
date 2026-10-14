package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.CreateInventoryCheckRequest;
import com.roms.dto.response.InventoryCheckResponse;
import com.roms.security.UserPrincipal;
import com.roms.service.InventoryCheckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory-checks")
@RequiredArgsConstructor
public class InventoryCheckController {

    private final InventoryCheckService inventoryCheckService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<InventoryCheckResponse>>> getInventoryChecks(
            @PageableDefault(size = 20, sort = "checkedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = inventoryCheckService.getInventoryChecks(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<InventoryCheckResponse>> getInventoryCheckById(@PathVariable Long id) {
        var response = inventoryCheckService.getInventoryCheckById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<InventoryCheckResponse>> createInventoryCheck(
            @Valid @RequestBody CreateInventoryCheckRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = (currentUser != null) ? currentUser.getId() : null;
        var response = inventoryCheckService.createInventoryCheck(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inventory check created successfully", response));
    }
}
