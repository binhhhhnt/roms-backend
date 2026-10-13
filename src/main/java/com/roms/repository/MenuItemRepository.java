package com.roms.repository;

import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByStatus(MenuItemStatus status);
}
