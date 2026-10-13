package com.roms.dto.response;

import com.roms.enums.MenuItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemSellabilityResponse {

    private Long menuItemId;
    private String menuItemName;
    private MenuItemStatus status;
    private boolean sellable;
    private String reason;
    private Integer requestedQuantity;
    private Integer maxPossibleQuantity;
    private List<InsufficientIngredientDto> insufficientIngredients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsufficientIngredientDto {
        private Long ingredientId;
        private String ingredientName;
        private String unit;
        private BigDecimal requiredQuantity;
        private BigDecimal currentStock;
    }
}
