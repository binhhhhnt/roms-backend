package com.roms.mapper;

import com.roms.dto.response.StockMovementResponse;
import com.roms.entity.StockMovement;
import org.springframework.stereotype.Component;

@Component
public class StockMovementMapper {

    public StockMovementResponse toResponse(StockMovement movement) {
        if (movement == null) {
            return null;
        }

        return StockMovementResponse.builder()
                .id(movement.getId())
                .ingredientId(movement.getIngredient() != null ? movement.getIngredient().getId() : null)
                .ingredientName(movement.getIngredient() != null ? movement.getIngredient().getName() : null)
                .unit(movement.getIngredient() != null ? movement.getIngredient().getUnit() : null)
                .movementType(movement.getMovementType())
                .quantityChange(movement.getQuantityChange())
                .balanceAfter(movement.getBalanceAfter())
                .purchaseItemId(movement.getPurchaseItem() != null ? movement.getPurchaseItem().getId() : null)
                .orderItemId(movement.getOrderItem() != null ? movement.getOrderItem().getId() : null)
                .inventoryCheckItemId(movement.getInventoryCheckItem() != null ? movement.getInventoryCheckItem().getId() : null)
                .note(movement.getNote())
                .occurredAt(movement.getOccurredAt())
                .build();
    }
}
