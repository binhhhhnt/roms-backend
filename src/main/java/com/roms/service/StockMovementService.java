package com.roms.service;

import com.roms.dto.response.StockMovementResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.InventoryCheckItem;
import com.roms.entity.OrderItem;
import com.roms.entity.PurchaseItem;
import com.roms.entity.StockMovement;
import com.roms.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface StockMovementService {

    StockMovement recordInventoryAdjustment(Ingredient ingredient, BigDecimal quantityChange,
                                            InventoryCheckItem checkItem, String note);

    StockMovement recordPurchase(Ingredient ingredient, BigDecimal quantityChange,
                                 PurchaseItem purchaseItem, String note);

    StockMovement recordOrderConsumption(Ingredient ingredient, BigDecimal quantityChange,
                                         OrderItem orderItem, String note);

    Page<StockMovementResponse> getMovements(Long ingredientId, StockMovementType movementType, Pageable pageable);
}
