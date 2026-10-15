package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.AiRecommendationRequest;
import com.roms.dto.response.AiRecommendationResponse;
import com.roms.service.AiAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cung cấp các API liên quan đến Trợ lý AI Gemini.
 * Cung cấp chức năng:
 * - Gợi ý món ăn thông minh theo khẩu vị, sở thích, số lượng người, dị ứng và ngân sách.
 * - Tự động xác thực ID món ăn và kiểm tra khả năng phục vụ thực tế (live recipe/stock sellability).
 */
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    /**
     * Gửi yêu cầu gợi ý món ăn tới Trợ lý AI Gemini.
     * 
     * Quyền truy cập:
     * - OWNER (Chủ nhà hàng)
     * - SERVICE_STAFF (Nhân viên phục vụ tư vấn cho khách)
     * - TABLE_ACCOUNT (Tablet đặt tại bàn ăn để thực khách tự tra cứu)
     *
     * @param request DTO chứa các tiêu chí lựa chọn món ăn
     * @return ResponseEntity bọc ApiResponse chứa danh sách món ăn gợi ý kèm lời nhắn AI
     */
    @PostMapping("/recommendations")
    @PreAuthorize("hasAnyRole('OWNER', 'SERVICE_STAFF', 'TABLE_ACCOUNT')")
    public ResponseEntity<ApiResponse<AiRecommendationResponse>> getRecommendations(
            @Valid @RequestBody(required = false) AiRecommendationRequest request) {

        AiRecommendationRequest safeRequest = request != null ? request : new AiRecommendationRequest();
        AiRecommendationResponse response = aiAssistantService.getRecommendations(safeRequest);

        return ResponseEntity.ok(ApiResponse.success("AI recommendations generated successfully", response));
    }
}
