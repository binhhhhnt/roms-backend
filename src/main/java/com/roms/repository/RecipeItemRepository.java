package com.roms.repository;

import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu công thức món ăn (recipe_items).
 */
@Repository
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    /**
     * Tìm danh sách công thức nguyên liệu của một món ăn cụ thể.
     *
     * @param menuItemId ID món ăn
     * @return Danh sách các thành phần nguyên liệu định lượng
     */
    @Query("SELECT r FROM RecipeItem r JOIN FETCH r.ingredient WHERE r.menuItem.id = :menuItemId")
    List<RecipeItem> findByMenuItemIdWithIngredient(@Param("menuItemId") Long menuItemId);

    /**
     * Tìm danh sách công thức nguyên liệu cho một tập hợp các món ăn (để tránh N+1 query).
     *
     * @param menuItemIds Danh sách ID các món ăn
     * @return Danh sách RecipeItem kèm thực thể Ingredient
     */
    @Query("SELECT r FROM RecipeItem r JOIN FETCH r.ingredient WHERE r.menuItem.id IN :menuItemIds")
    List<RecipeItem> findByMenuItemIdInWithIngredient(@Param("menuItemIds") Collection<Long> menuItemIds);

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
