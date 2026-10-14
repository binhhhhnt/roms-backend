package com.roms.repository;

import com.roms.entity.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

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
}
