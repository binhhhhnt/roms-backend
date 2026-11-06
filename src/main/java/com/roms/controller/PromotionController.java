package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.PromotionWriteRequest;
import com.roms.dto.response.PromotionDto;
import com.roms.dto.response.PromotionResponse;
import com.roms.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Page<PromotionResponse>>> getPromotions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PromotionResponse> page = promotionService.getPromotions(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách khuyến mãi thành công", page));
    }

    @GetMapping("/valid")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF')")
    public ResponseEntity<ApiResponse<List<PromotionDto>>> getValidPromotions() {
        List<PromotionDto> validPromotions = promotionService.getValidPromotions();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách khuyến mãi khả dụng thành công", validPromotions));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> getPromotionById(@PathVariable Long id) {
        PromotionResponse response = promotionService.getPromotionById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin khuyến mãi thành công", response));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> createPromotion(
            @Valid @RequestBody PromotionWriteRequest request) {
        PromotionResponse response = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo mã khuyến mãi thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody PromotionWriteRequest request) {
        PromotionResponse response = promotionService.updatePromotion(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật khuyến mãi thành công", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa mã khuyến mãi thành công", null));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> toggleActive(@PathVariable Long id) {
        PromotionResponse response = promotionService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái khuyến mãi thành công", response));
    }
}

