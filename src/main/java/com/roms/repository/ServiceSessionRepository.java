package com.roms.repository;

import com.roms.entity.ServiceSession;
import com.roms.enums.ServiceSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceSessionRepository extends JpaRepository<ServiceSession, Long> {

    Optional<ServiceSession> findByTableIdAndStatus(Long tableId, ServiceSessionStatus status);

    long countByStatus(ServiceSessionStatus status);

    boolean existsByTableIdAndStatus(Long tableId, ServiceSessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServiceSession s " +
           "JOIN FETCH s.table " +
           "LEFT JOIN FETCH s.openedBy " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE s.id = :id")
    Optional<ServiceSession> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT s FROM ServiceSession s " +
           "JOIN FETCH s.table t " +
           "LEFT JOIN FETCH s.openedBy " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE (:tableId IS NULL OR t.id = :tableId) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:search IS NULL OR LOWER(s.sessionCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.tableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND s.openedAt >= :fromDateTime AND s.openedAt < :toDateTime",
           countQuery = "SELECT COUNT(s) FROM ServiceSession s JOIN s.table t " +
           "WHERE (:tableId IS NULL OR t.id = :tableId) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:search IS NULL OR LOWER(s.sessionCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(t.tableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND s.openedAt >= :fromDateTime AND s.openedAt < :toDateTime")
    Page<ServiceSession> findWithFilters(
            @Param("tableId") Long tableId,
            @Param("status") ServiceSessionStatus status,
            @Param("search") String search,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime,
            Pageable pageable
    );

    @Query("SELECT s FROM ServiceSession s " +
           "JOIN FETCH s.table " +
           "LEFT JOIN FETCH s.openedBy " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE s.id = :id")
    Optional<ServiceSession> findByIdWithDetails(@Param("id") Long id);

    List<ServiceSession> findByTableId(Long tableId);
}
