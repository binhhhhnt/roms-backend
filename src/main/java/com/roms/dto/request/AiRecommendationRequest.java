package com.roms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO yêu cầu gợi ý món ăn gửi tới Trợ lý AI (Gemini Assistant).
 */
@Getter
@Setter
@Builder
public class AiRecommendationRequest {

    public AiRecommendationRequest() {
    }

    public AiRecommendationRequest(String preferences, Integer partySize, String allergies,
                                  String budgetRange, String message) {
        this.preferences = preferences;
        this.partySize = partySize;
        this.allergies = allergies;
        this.budgetRange = budgetRange;
        this.message = message;
    }

    /**
     * Sở thích ăn uống của khách hàng (ví dụ: "thích ăn cay", "thích đồ cuốn", "đồ ăn thanh đạm").
     */
    @Size(max = 255, message = "Preferences must not exceed 255 characters")
    private String preferences;

    /**
     * Số lượng người trong bàn ăn (ví dụ: 1, 2, 4, 6...).
     */
    @Min(value = 1, message = "Party size must be at least 1")
    @Max(value = 50, message = "Party size must not exceed 50")
    private Integer partySize;

    /**
     * Các thành phần dị ứng hoặc kiêng cử (ví dụ: "dị ứng hải sản", "không ăn hành", "kiêng bột ngọt").
     */
    @Size(max = 255, message = "Allergies must not exceed 255 characters")
    private String allergies;

    /**
     * Mức ngân sách dự kiến (ví dụ: "dưới 200k", "khoảng 500k", "tầm trung").
     */
    @Size(max = 100, message = "Budget range must not exceed 100 characters")
    private String budgetRange;

    /**
     * Câu hỏi hoặc yêu cầu cụ thể từ khách hàng gửi tới trợ lý AI.
     */
    @Size(max = 500, message = "Message must not exceed 500 characters")
    private String message;
}
