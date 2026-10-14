package com.roms.service.impl;

import com.roms.dto.response.StockMovementResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.InventoryCheckItem;
import com.roms.entity.OrderItem;
import com.roms.entity.PurchaseItem;
import com.roms.entity.StockMovement;
import com.roms.enums.StockMovementType;
import com.roms.mapper.StockMovementMapper;
import com.roms.repository.StockMovementRepository;
import com.roms.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final StockMovementMapper stockMovementMapper;

    @Override
    @Transactional
    public StockMovement recordInventoryAdjustment(Ingredient ingredient, BigDecimal quantityChange,
                                                   InventoryCheckItem checkItem, String note) {
        StockMovement movement = StockMovement.builder()
                .ingredient(ingredient)
                .movementType(StockMovementType.INVENTORY_ADJUSTMENT)
                .quantityChange(quantityChange)
                .balanceAfter(ingredient.getCurrentStock())
                .inventoryCheckItem(checkItem)
                .note(note)
                .build();
        return stockMovementRepository.save(movement);
    }

    @Override
    @Transactional
    public StockMovement recordPurchase(Ingredient ingredient, BigDecimal quantityChange,
                                        PurchaseItem purchaseItem, String note) {
        StockMovement movement = StockMovement.builder()
                .ingredient(ingredient)
                .movementType(StockMovementType.PURCHASE)
                .quantityChange(quantityChange)
                .balanceAfter(ingredient.getCurrentStock())
                .purchaseItem(purchaseItem)
                .note(note)
                .build();
        return stockMovementRepository.save(movement);
    }

    @Override
    @Transactional
    public StockMovement recordOrderConsumption(Ingredient ingredient, BigDecimal quantityChange,
                                                OrderItem orderItem, String note) {
        StockMovement movement = StockMovement.builder()
                .ingredient(ingredient)
                .movementType(StockMovementType.ORDER_CONSUMPTION)
                .quantityChange(quantityChange)
                .balanceAfter(ingredient.getCurrentStock())
                .orderItem(orderItem)
                .note(note)
                .build();
        return stockMovementRepository.save(movement);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovements(Long ingredientId, StockMovementType movementType, Pageable pageable) {
        Page<StockMovement> page;
        if (ingredientId != null && movementType != null) {
            page = stockMovementRepository.findByIngredientIdAndMovementType(ingredientId, movementType, pageable);
        } else if (ingredientId != null) {
            page = stockMovementRepository.findByIngredientId(ingredientId, pageable);
        } else if (movementType != null) {
            page = stockMovementRepository.findByMovementType(movementType, pageable);
        } else {
            page = stockMovementRepository.findAll(pageable);
        }
        return page.map(stockMovementMapper::toResponse);
    }
}
