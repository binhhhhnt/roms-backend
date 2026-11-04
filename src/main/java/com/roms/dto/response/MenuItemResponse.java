package com.roms.dto.response;

import com.roms.enums.MenuItemStatus;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter @Builder
public class MenuItemResponse {
    private Long id;
    private Long categoryId;
    private Long taxCategoryId;
    private BigDecimal taxRate;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean isFeatured;
    private MenuItemStatus status;

    public Long getTax_category_id() {
        return taxCategoryId;
    }

    public BigDecimal getTax_rate() {
        return taxRate;
    }
}
