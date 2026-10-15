package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO chi tiết một món ăn được Trợ lý AI gợi ý cho khách hàng.
 */
@Getter
@Setter
@Builder
public class AiRecommendationItem {

    public AiRecommendationItem() {
    }

    public AiRecommendationItem(Long menuItemId, String name, BigDecimal price,
                                String categoryName, String description, String reason, Boolean isSellable) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.categoryName = categoryName;
        this.description = description;
        this.reason = reason;
        this.isSellable = isSellable != null ? isSellable : true;
    }

    /**
     * ID định danh của món ăn trong cơ sở dữ liệu.
     */
    private Long menuItemId;

    /**
     * Tên món ăn.
     */
    private String name;

    /**
     * Giá bán hiện tại.
     */
    private BigDecimal price;

    /**
     * Tên danh mục món ăn (ví dụ: Món chính, Khai vị, Đồ uống...).
     */
    private String categoryName;

    /**
     * Mô tả chi tiết về món ăn.
     */
    private String description;

    /**
     * Lý do món ăn này được AI gợi ý dựa trên sở thích/yêu cầu của khách.
     */
    private String reason;

    /**
     * Trạng thái sẵn sàng phục vụ thực tế (đã xác thực qua trạng thái và kho nguyên liệu).
     */
    private Boolean isSellable;
}
