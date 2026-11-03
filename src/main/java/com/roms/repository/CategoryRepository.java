package com.roms.repository;

import com.roms.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c order by c.displayOrder asc, c.id asc")
    List<Category> findAllForUpdateInDisplayOrder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForUpdate(@Param("id") Long id);

    Page<Category> findByIsActiveTrue(Pageable pageable);
}
