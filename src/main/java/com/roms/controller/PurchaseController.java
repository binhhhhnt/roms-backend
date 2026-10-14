package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.CreatePurchaseRequest;
import com.roms.dto.request.UpdatePurchaseItemRequest;
import com.roms.dto.response.PurchaseDetailResponse;
import com.roms.dto.response.PurchaseResponse;
import com.roms.security.UserPrincipal;
import com.roms.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseResponse>>> getPurchases(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "purchaseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = purchaseService.getPurchases(fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<PurchaseDetailResponse>> getPurchaseById(@PathVariable Long id) {
        var response = purchaseService.getPurchaseById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<PurchaseDetailResponse>> createPurchase(
            @Valid @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = (currentUser != null) ? currentUser.getId() : null;
        var response = purchaseService.createPurchase(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase created successfully", response));
    }

    @PatchMapping("/{purchaseId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('OWNER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<PurchaseDetailResponse>> correctPurchaseItem(
            @PathVariable Long purchaseId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdatePurchaseItemRequest request) {
        var response = purchaseService.correctPurchaseItem(purchaseId, itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Purchase item corrected successfully", response));
    }
}
