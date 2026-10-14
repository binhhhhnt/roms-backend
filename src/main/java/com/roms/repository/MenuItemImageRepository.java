package com.roms.repository;

import com.roms.entity.MenuItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemImageRepository extends JpaRepository<MenuItemImage, Long> {
    @Query("select i from MenuItemImage i join fetch i.menuItem where i.menuItem.id in :menuIds "
            + "order by i.menuItem.id, i.displayOrder, i.id")
    List<MenuItemImage> findCatalogImages(@Param("menuIds") Collection<Long> menuIds);
    Optional<MenuItemImage> findByIdAndMenuItemId(Long id, Long menuItemId);
}
