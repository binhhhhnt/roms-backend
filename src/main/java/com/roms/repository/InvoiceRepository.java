package com.roms.repository;

import com.roms.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByServiceSessionId(Long serviceSessionId);

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    boolean existsByServiceSessionId(Long serviceSessionId);

    @Query("SELECT i FROM Invoice i " +
           "JOIN FETCH i.serviceSession s " +
           "JOIN FETCH s.table t " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE i.id = :id")
    Optional<Invoice> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i " +
           "JOIN FETCH i.serviceSession s " +
           "JOIN FETCH s.table t " +
           "LEFT JOIN FETCH s.closedBy " +
           "WHERE s.id = :sessionId")
    Optional<Invoice> findBySessionIdWithDetails(@Param("sessionId") Long sessionId);

    @Query(value = "SELECT i FROM Invoice i " +
           "JOIN i.serviceSession s " +
           "JOIN s.table t " +
           "WHERE (:tableId IS NULL OR t.id = :tableId) " +
           "AND (cast(:startDate as timestamp) IS NULL OR i.createdAt >= :startDate) " +
           "AND (cast(:endDate as timestamp) IS NULL OR i.createdAt <= :endDate)",
           countQuery = "SELECT count(i) FROM Invoice i " +
           "JOIN i.serviceSession s " +
           "JOIN s.table t " +
           "WHERE (:tableId IS NULL OR t.id = :tableId) " +
           "AND (cast(:startDate as timestamp) IS NULL OR i.createdAt >= :startDate) " +
           "AND (cast(:endDate as timestamp) IS NULL OR i.createdAt <= :endDate)")
    Page<Invoice> findWithFilters(
            @Param("tableId") Long tableId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(i.finalAmount), 0) FROM Invoice i " +
           "WHERE i.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumFinalAmountBetweenDates(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COALESCE(SUM(i.discountAmount), 0) FROM Invoice i " +
           "WHERE i.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumDiscountAmountBetweenDates(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}

