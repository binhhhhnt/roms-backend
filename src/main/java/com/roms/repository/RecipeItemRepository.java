package com.roms.repository;

import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    @Query("SELECT r FROM RecipeItem r JOIN FETCH r.ingredient WHERE r.menuItem.id = :menuItemId")
    List<RecipeItem> findByMenuItemIdWithIngredient(@Param("menuItemId") Long menuItemId);

    List<RecipeItem> findByMenuItemId(Long menuItemId);

    Optional<RecipeItem> findByMenuItemIdAndIngredientId(Long menuItemId, Long ingredientId);

    boolean existsByMenuItemIdAndIngredientId(Long menuItemId, Long ingredientId);

    @Modifying
    @Query("DELETE FROM RecipeItem r WHERE r.menuItem.id = :menuItemId")
    void deleteByMenuItemId(@Param("menuItemId") Long menuItemId);

    @Modifying
    @Query("DELETE FROM RecipeItem r WHERE r.menuItem.id = :menuItemId AND r.ingredient.id = :ingredientId")
    void deleteByMenuItemIdAndIngredientId(@Param("menuItemId") Long menuItemId, @Param("ingredientId") Long ingredientId);

    boolean existsByIngredientId(Long ingredientId);

    @Query("SELECT COUNT(r) > 0 FROM RecipeItem r WHERE r.ingredient.id = :ingredientId AND r.menuItem.status <> :excludedStatus")
    boolean existsByIngredientIdAndMenuItemStatusNot(
            @Param("ingredientId") Long ingredientId,
            @Param("excludedStatus") MenuItemStatus excludedStatus
    );
}
