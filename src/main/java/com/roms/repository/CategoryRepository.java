package com.roms.repository;

import com.roms.entity.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu danh mục món ăn (categories).
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c order by c.displayOrder asc, c.id asc")
    List<Category> findAllForUpdateInDisplayOrder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForUpdate(@Param("id") Long id);

    Page<Category> findByIsActiveTrue(Pageable pageable);

    /**
     * Tìm kiếm danh mục theo tên.
     *
     * @param name Tên danh mục
     * @return Optional<Category>
     */
    Optional<Category> findByName(String name);

    /**
     * Kiểm tra xem tên danh mục đã tồn tại chưa.
     *
     * @param name Tên danh mục
     * @return true nếu đã tồn tại
     */
    boolean existsByName(String name);
}
