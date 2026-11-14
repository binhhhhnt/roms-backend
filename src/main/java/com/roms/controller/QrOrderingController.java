package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.QrOrderRequestCreateRequest;
import com.roms.dto.response.*;
import com.roms.service.QrOrderingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qr/{qrToken}")
@RequiredArgsConstructor
public class QrOrderingController {

    private final QrOrderingService qrOrderingService;

    @GetMapping("/context")
    public ResponseEntity<ApiResponse<QrContextResponse>> resolveContext(@PathVariable String qrToken) {
        return ResponseEntity.ok(ApiResponse.success("QR context resolved successfully",
                qrOrderingService.resolveContext(qrToken)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> getCategories(
            @PathVariable String qrToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.ASC, "displayOrder", "id"));
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully",
                qrOrderingService.getCategories(qrToken, pageable)));
    }

    @GetMapping("/menu")
    public ResponseEntity<ApiResponse<PageResponse<MenuItemReadResponse>>> getMenu(
            @PathVariable String qrToken,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(ApiResponse.success("Menu retrieved successfully",
                qrOrderingService.getMenu(qrToken, categoryId, pageable)));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<QrOrderRequestResponse>> submitQrOrder(
            @PathVariable String qrToken,
            @Valid @RequestBody QrOrderRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("QR order request submitted successfully",
                        qrOrderingService.submitQrOrder(qrToken, request)));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<QrOrdersTrackingResponse>> getSessionOrders(@PathVariable String qrToken) {
        return ResponseEntity.ok(ApiResponse.success("Session orders retrieved successfully",
                qrOrderingService.getSessionOrders(qrToken)));
    }

    @PostMapping("/call-staff")
    public ResponseEntity<ApiResponse<Void>> callStaff(@PathVariable String qrToken) {
        qrOrderingService.callStaff(qrToken);
        return ResponseEntity.ok(ApiResponse.success("Staff call sent", null));
    }
}
