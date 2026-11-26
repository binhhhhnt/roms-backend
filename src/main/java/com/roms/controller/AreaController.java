package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.CreateAreaRequest;
import com.roms.dto.request.UpdateAreaRequest;
import com.roms.dto.request.UpdateAreaStatusRequest;
import com.roms.dto.response.AreaResponse;
import com.roms.service.AreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<List<AreaResponse>>> getAreas(
            @RequestParam(required = false) Boolean isActive) {
        return ResponseEntity.ok(ApiResponse.success("Areas retrieved successfully", areaService.getAreas(isActive)));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<AreaResponse>> createArea(@Valid @RequestBody CreateAreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Area created successfully", areaService.createArea(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<AreaResponse>> updateArea(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Area updated successfully", areaService.updateArea(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<AreaResponse>> updateAreaStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAreaStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Area status updated successfully", areaService.updateAreaStatus(id, request)));
    }
}
