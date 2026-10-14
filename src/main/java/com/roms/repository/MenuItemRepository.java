package com.roms.repository;

import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository truy xuất dữ liệu món ăn trong thực đơn (menu_items).
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    /**
     * Tìm danh sách món ăn theo trạng thái kinh doanh.
     *
     * @param status Trạng thái món (AVAILABLE, TEMPORARILY_UNAVAILABLE, DISCONTINUED)
     * @return Danh sách món ăn
     */
    List<MenuItem> findByStatus(MenuItemStatus status);

    /**
     * Lấy danh sách món ăn loại trừ trạng thái chỉ định (thường dùng để loại DISCONTINUED),
     * đồng thời eager fetch Category để tránh lỗi N+1 query.
     *
     * @param status Trạng thái cần loại trừ
     * @return Danh sách món ăn kèm Category
     */
    @Query("SELECT m FROM MenuItem m JOIN FETCH m.category WHERE m.status != :status")
    List<MenuItem> findByStatusNotWithCategory(@Param("status") MenuItemStatus status);

    /**
     * Tìm các món ăn theo danh sách ID kèm Category.
     *
     * @param ids Tập hợp ID món ăn
     * @return Danh sách món ăn kèm Category
     */
    @Query("SELECT m FROM MenuItem m JOIN FETCH m.category WHERE m.id IN :ids")
    List<MenuItem> findByIdInWithCategory(@Param("ids") Collection<Long> ids);

    /**
     * Tìm các món ăn nổi bật (isFeatured = true) và đang mở bán (AVAILABLE) để dùng khi gợi ý mặc định.
     *
     * @return Danh sách món ăn nổi bật kèm Category
     */
    @Query("SELECT m FROM MenuItem m JOIN FETCH m.category WHERE m.isFeatured = true AND m.status = 'AVAILABLE'")
    List<MenuItem> findFeaturedAvailableWithCategory();
}
