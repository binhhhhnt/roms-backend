package com.roms.dto.response;

import com.roms.enums.MenuItemStatus;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter @Builder
public class MenuItemResponse {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean isFeatured;
    private MenuItemStatus status;
}
