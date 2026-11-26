package com.roms.repository;

import com.roms.entity.Area;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Area> findAllByOrderByNameAsc();

    List<Area> findByIsActiveTrueOrderByNameAsc();

    List<Area> findByIsActiveOrderByNameAsc(Boolean isActive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Area a WHERE a.id = :id")
    Optional<Area> findByIdForUpdate(@Param("id") Long id);
}
