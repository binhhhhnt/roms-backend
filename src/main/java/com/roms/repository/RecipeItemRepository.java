package com.roms.repository;

import com.roms.entity.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    @Query("SELECT r FROM RecipeItem r JOIN FETCH r.ingredient WHERE r.menuItem.id = :menuItemId")
    List<RecipeItem> findByMenuItemIdWithIngredient(@Param("menuItemId") Long menuItemId);

    List<RecipeItem> findByMenuItemId(Long menuItemId);
}
