package com.roms.repository;

import com.roms.entity.ServiceSession;
import com.roms.enums.ServiceSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceSessionRepository extends JpaRepository<ServiceSession, Long> {

    Optional<ServiceSession> findByTableIdAndStatus(Long tableId, ServiceSessionStatus status);

    boolean existsByTableIdAndStatus(Long tableId, ServiceSessionStatus status);

    @Query("SELECT s FROM ServiceSession s " +
           "JOIN FETCH s.table " +
           "JOIN FETCH s.openedBy " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE (:tableId IS NULL OR s.table.id = :tableId) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<ServiceSession> findWithFilters(
            @Param("tableId") Long tableId,
            @Param("status") ServiceSessionStatus status,
            Pageable pageable
    );

    @Query("SELECT s FROM ServiceSession s " +
           "JOIN FETCH s.table " +
           "JOIN FETCH s.openedBy " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE s.id = :id")
    Optional<ServiceSession> findByIdWithDetails(@Param("id") Long id);

    List<ServiceSession> findByTableId(Long tableId);
}

