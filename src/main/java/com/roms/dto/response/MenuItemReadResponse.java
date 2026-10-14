package com.roms.dto.response;

import com.roms.enums.MenuItemStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class MenuItemReadResponse {
    private Long id;
    private CategoryResponse category;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean isFeatured;
    private MenuItemStatus status;
    private List<MenuItemImageResponse> images;
    private Availability availability;

    @Getter
    @Builder
    public static class Availability {
        private boolean sellable;
        private String reason;
        private Integer requestedQuantity;
        private Integer maxPossibleQuantity;
    }
}
