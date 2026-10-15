package com.roms.repository;

import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    List<MenuItem> findByStatus(MenuItemStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);
}
