package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO phản hồi tổng thể từ Trợ lý AI (Gemini Assistant) tới khách hàng.
 */
@Getter
@Setter
@Builder
public class AiRecommendationResponse {

    public AiRecommendationResponse() {
        this.recommendations = new ArrayList<>();
    }

    public AiRecommendationResponse(String message, List<AiRecommendationItem> recommendations) {
        this.message = message;
        this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
    }

    /**
     * Lời chào hoặc lời khuyên/nhận xét tổng quan thân thiện từ Trợ lý AI.
     */
    private String message;

    /**
     * Danh sách các món ăn cụ thể được gợi ý kèm giải thích lý do.
     */
    @Builder.Default
    private List<AiRecommendationItem> recommendations = new ArrayList<>();
}
