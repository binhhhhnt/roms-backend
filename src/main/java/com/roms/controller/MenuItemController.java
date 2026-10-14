package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;
import com.roms.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequestMapping("/api/menu-items") @RequiredArgsConstructor
public class MenuItemController {
    private final MenuItemService service;
    @PostMapping
    public ResponseEntity<ApiResponse<MenuItemResponse>> create(@Valid @RequestBody MenuItemWriteRequest request) {
        var response = service.createMenuItem(request);
        return ResponseEntity.created(URI.create("/api/menu-items/" + response.getId()))
                .body(ApiResponse.success("Menu item created successfully", response));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MenuItemResponse>> update(@PathVariable Long id, @Valid @RequestBody MenuItemWriteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Menu item updated successfully", service.updateMenuItem(id, request)));
    }
}
