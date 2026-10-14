package com.roms.repository;

import com.roms.entity.MenuItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MenuItemImageRepository extends JpaRepository<MenuItemImage, Long> {
    Optional<MenuItemImage> findByIdAndMenuItemId(Long id, Long menuItemId);
}
