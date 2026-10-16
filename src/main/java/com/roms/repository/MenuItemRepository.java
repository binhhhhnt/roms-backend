package com.roms.repository;

import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu món ăn trong thực đơn (menu_items).
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @EntityGraph(attributePaths = "category")
    @Query("select m from MenuItem m where (:categoryId is null or m.category.id = :categoryId) "
            + "and m.status in :statuses and (:tablet = false or m.category.isActive = true)")
    Page<MenuItem> findCatalog(@Param("categoryId") Long categoryId,
                             @Param("statuses") Collection<MenuItemStatus> statuses,
                             @Param("tablet") boolean tablet, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("select m from MenuItem m where m.id = :id")
    Optional<MenuItem> findCatalogById(@Param("id") Long id);

    /**
     * Tìm danh sách món ăn theo trạng thái kinh doanh.
     *
     * @param status Trạng thái món (AVAILABLE, TEMPORARILY_UNAVAILABLE, DISCONTINUED)
     * @return Danh sách món ăn
     */
    List<MenuItem> findByStatus(MenuItemStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);

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
