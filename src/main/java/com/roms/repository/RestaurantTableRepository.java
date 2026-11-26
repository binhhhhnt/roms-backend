package com.roms.repository;

import com.roms.entity.RestaurantTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    @EntityGraph(attributePaths = {"area"})
    Optional<RestaurantTable> findByTableCode(String tableCode);

    @EntityGraph(attributePaths = {"area"})
    Optional<RestaurantTable> findWithAreaById(Long id);

    boolean existsByTableCode(String tableCode);

    long countByAreaIdAndIsActiveTrue(Long areaId);

    @Query("SELECT t.area.id, COUNT(t) FROM RestaurantTable t WHERE t.isActive = true GROUP BY t.area.id")
    List<Object[]> countActiveTablesGroupByArea();

    @Query(value = "SELECT t FROM RestaurantTable t JOIN FETCH t.area a WHERE " +
           "(:isActive IS NULL OR t.isActive = :isActive) AND " +
           "(:areaId IS NULL OR a.id = :areaId) AND " +
           "(:search IS NULL OR LOWER(t.tableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))",
           countQuery = "SELECT COUNT(t) FROM RestaurantTable t JOIN t.area a WHERE " +
           "(:isActive IS NULL OR t.isActive = :isActive) AND " +
           "(:areaId IS NULL OR a.id = :areaId) AND " +
           "(:search IS NULL OR LOWER(t.tableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<RestaurantTable> findWithFilters(
            @Param("isActive") Boolean isActive,
            @Param("areaId") Long areaId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.name FROM RestaurantTable t JOIN t.area a WHERE a.isActive = true ORDER BY a.name")
    List<String> findDistinctAreas();
}
