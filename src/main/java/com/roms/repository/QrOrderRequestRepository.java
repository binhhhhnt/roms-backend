package com.roms.repository;

import com.roms.entity.QrOrderRequest;
import com.roms.enums.QrOrderRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QrOrderRequestRepository extends JpaRepository<QrOrderRequest, Long> {

    boolean existsByServiceSessionIdAndStatus(Long serviceSessionId, QrOrderRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT r FROM QrOrderRequest r " +
           "JOIN FETCH r.serviceSession s " +
           "JOIN FETCH s.table t " +
           "LEFT JOIN FETCH r.items i " +
           "LEFT JOIN FETCH i.menuItem m " +
           "LEFT JOIN FETCH r.confirmedBy " +
           "LEFT JOIN FETCH r.cancelledBy " +
           "LEFT JOIN FETCH r.confirmedOrder " +
           "WHERE r.id = :id")
    Optional<QrOrderRequest> findByIdForUpdateWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT r FROM QrOrderRequest r " +
           "JOIN FETCH r.serviceSession s " +
           "JOIN FETCH s.table t " +
           "LEFT JOIN FETCH r.items i " +
           "LEFT JOIN FETCH i.menuItem m " +
           "LEFT JOIN FETCH r.confirmedBy " +
           "LEFT JOIN FETCH r.cancelledBy " +
           "LEFT JOIN FETCH r.confirmedOrder " +
           "WHERE s.id = :sessionId " +
           "ORDER BY r.createdAt DESC, r.id DESC")
    List<QrOrderRequest> findByServiceSessionIdWithDetails(@Param("sessionId") Long sessionId);

    @Query("SELECT DISTINCT r FROM QrOrderRequest r " +
           "JOIN FETCH r.serviceSession s " +
           "JOIN FETCH s.table t " +
           "LEFT JOIN FETCH r.items i " +
           "LEFT JOIN FETCH i.menuItem m " +
           "LEFT JOIN FETCH r.confirmedBy " +
           "LEFT JOIN FETCH r.cancelledBy " +
           "LEFT JOIN FETCH r.confirmedOrder " +
           "WHERE r.status = :status " +
           "ORDER BY r.createdAt ASC, r.id ASC")
    List<QrOrderRequest> findByStatusWithDetails(@Param("status") QrOrderRequestStatus status);
}
