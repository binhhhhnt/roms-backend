package com.roms.repository;

import com.roms.entity.InventoryCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryCheckRepository extends JpaRepository<InventoryCheck, Long> {

    Page<InventoryCheck> findAllByOrderByCheckedAtDesc(Pageable pageable);
}
