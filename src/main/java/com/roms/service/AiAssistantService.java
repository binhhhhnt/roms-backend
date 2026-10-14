package com.roms.service;

import com.roms.dto.request.AiRecommendationRequest;
import com.roms.dto.response.AiRecommendationResponse;

/**
 * Service định nghĩa các chức năng của Trợ lý AI Gemini hỗ trợ ẩm thực và gợi ý thực đơn.
 */
public interface AiAssistantService {

    /**
     * Gợi ý món ăn thông minh cho khách hàng dựa trên sở thích, khẩu vị, dị ứng, số lượng người và ngân sách.
     * Áp dụng hai lớp xác thực an toàn bắt buộc:
     * 1. Xác thực ID món ăn (ID validation): Đảm bảo ID do AI trả về thực sự tồn tại trong danh mục kinh doanh.
     * 2. Kiểm tra khả năng phục vụ thực tế (Live sellability check): Kiểm tra trực tiếp từ PostgreSQL xem món có đang
     *    ở trạng thái AVAILABLE và kho nguyên liệu có đủ số lượng theo công thức (recipe_items) hay không.
     *
     * @param request Yêu cầu gợi ý từ khách hàng
     * @return AiRecommendationResponse Danh sách món ăn được gợi ý kèm lời nhắn thân thiện
     */
    AiRecommendationResponse getRecommendations(AiRecommendationRequest request);
}
