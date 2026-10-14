package com.roms.repository;

import com.roms.entity.StockMovement;
import com.roms.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByIngredientId(Long ingredientId);

    List<StockMovement> findByOrderItemId(Long orderItemId);

    List<StockMovement> findByPurchaseItemId(Long purchaseItemId);

    List<StockMovement> findByInventoryCheckItemId(Long inventoryCheckItemId);

    Page<StockMovement> findByIngredientIdAndMovementType(Long ingredientId, StockMovementType movementType, Pageable pageable);

    Page<StockMovement> findByIngredientId(Long ingredientId, Pageable pageable);

    Page<StockMovement> findByMovementType(StockMovementType movementType, Pageable pageable);
}
