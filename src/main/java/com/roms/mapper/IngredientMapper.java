package com.roms.mapper;

import com.roms.dto.request.CreateIngredientRequest;
import com.roms.dto.response.IngredientResponse;
import com.roms.entity.Ingredient;
import com.roms.enums.StockStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class IngredientMapper {

    public Ingredient toEntity(CreateIngredientRequest request) {
        return Ingredient.builder()
                .name(request.getName().trim())
                .unit(request.getUnit().trim())
                .currentStock(BigDecimal.ZERO)
                .minimumStock(request.getMinimumStock())
                .isActive(true)
                .build();
    }

    public IngredientResponse toResponse(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }

        return IngredientResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .unit(ingredient.getUnit())
                .currentStock(ingredient.getCurrentStock())
                .minimumStock(ingredient.getMinimumStock())
                .isActive(ingredient.getIsActive())
                .stockStatus(computeStockStatus(ingredient.getCurrentStock(), ingredient.getMinimumStock()))
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }

    public StockStatus computeStockStatus(BigDecimal currentStock, BigDecimal minimumStock) {
        if (currentStock == null || currentStock.compareTo(BigDecimal.ZERO) <= 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        if (minimumStock != null && currentStock.compareTo(minimumStock) <= 0) {
            return StockStatus.LOW;
        }
        return StockStatus.NORMAL;
    }
}
