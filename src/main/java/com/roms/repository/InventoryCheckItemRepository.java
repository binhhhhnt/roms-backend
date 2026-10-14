package com.roms.repository;

import com.roms.entity.InventoryCheckItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryCheckItemRepository extends JpaRepository<InventoryCheckItem, Long> {

    List<InventoryCheckItem> findByInventoryCheckIdOrderByIdAsc(Long inventoryCheckId);
}
