package com.roms.repository;

import com.roms.entity.RestaurantTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByTableCode(String tableCode);

    boolean existsByTableCode(String tableCode);

    @Query("SELECT t FROM RestaurantTable t WHERE " +
           "(:isActive IS NULL OR t.isActive = :isActive) AND " +
           "(:area IS NULL OR t.area = :area) AND " +
           "(:search IS NULL OR LOWER(t.tableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "   OR LOWER(t.area) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RestaurantTable> findWithFilters(
            @Param("isActive") Boolean isActive,
            @Param("area") String area,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT DISTINCT t.area FROM RestaurantTable t WHERE t.area IS NOT NULL ORDER BY t.area")
    java.util.List<String> findDistinctAreas();
}
