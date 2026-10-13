package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.UpdateMenuStatusRequest;
import com.roms.dto.response.MenuItemSellabilityResponse;
import com.roms.dto.response.MenuItemStatusResponse;
import com.roms.service.MenuAvailabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
public class MenuAvailabilityController {

    private final MenuAvailabilityService menuAvailabilityService;

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'KITCHEN_STAFF')")
    public ResponseEntity<ApiResponse<MenuItemStatusResponse>> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateMenuStatusRequest request) {
        MenuItemStatusResponse response = menuAvailabilityService.updateMenuStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Menu item status updated successfully", response));
    }

    @GetMapping("/{id}/sellability")
    public ResponseEntity<ApiResponse<MenuItemSellabilityResponse>> checkSellability(
            @PathVariable("id") Long id,
            @RequestParam(name = "quantity", defaultValue = "1") @Min(1) Integer quantity) {
        MenuItemSellabilityResponse response = menuAvailabilityService.checkSellability(id, quantity);
        return ResponseEntity.ok(ApiResponse.success("Menu item sellability checked successfully", response));
    }
}
