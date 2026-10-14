package com.roms.repository;

import com.roms.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository truy xuất dữ liệu danh mục món ăn (categories).
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

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
