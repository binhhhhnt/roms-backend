package com.roms.repository;

import com.roms.entity.Ingredient;
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
import java.util.Set;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT i.name FROM Ingredient i WHERE LOWER(i.name) IN :names")
    List<String> findExistingNamesIgnoreCase(@Param("names") Set<String> names);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Ingredient> findByIsActiveTrue();

    Page<Ingredient> findByIsActive(Boolean isActive, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Ingredient i WHERE i.id = :id")
    Optional<Ingredient> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Ingredient i WHERE i.id = :id")
    Optional<Ingredient> findByIdWithPessimisticLock(@Param("id") Long id);

    @Query("SELECT COUNT(i) FROM Ingredient i WHERE i.isActive = true AND i.minimumStock IS NOT NULL AND i.currentStock <= i.minimumStock")
    long countLowStockIngredients();
}
