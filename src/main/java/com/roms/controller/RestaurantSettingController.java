package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.UpdateSettingRequest;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.service.RestaurantSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class RestaurantSettingController {

    private final RestaurantSettingService settingService;

    @GetMapping
    public ResponseEntity<ApiResponse<RestaurantSettingResponse>> getSettings() {
        RestaurantSettingResponse response = settingService.getSettings();
        return ResponseEntity.ok(ApiResponse.success("Restaurant settings retrieved successfully", response));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<RestaurantSettingResponse>> updateSettings(
            @Valid @RequestBody UpdateSettingRequest request) {
        RestaurantSettingResponse response = settingService.updateSettings(request);
        return ResponseEntity.ok(ApiResponse.success("Restaurant settings updated successfully", response));
    }
}

