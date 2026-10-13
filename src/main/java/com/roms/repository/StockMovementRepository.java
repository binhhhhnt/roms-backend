package com.roms.repository;

import com.roms.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByIngredientId(Long ingredientId);
    List<StockMovement> findByOrderItemId(Long orderItemId);
    List<StockMovement> findByOrderItemIdIn(List<Long> orderItemIds);
}
