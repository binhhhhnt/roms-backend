package com.roms.repository;

import com.roms.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Purchase p WHERE " +
           "p.purchaseDate BETWEEN :startDate AND :endDate")
    BigDecimal sumPurchasesBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT p.purchaseDate, COALESCE(SUM(p.totalAmount), 0) FROM Purchase p WHERE " +
           "p.purchaseDate BETWEEN :startDate AND :endDate " +
           "GROUP BY p.purchaseDate ORDER BY p.purchaseDate ASC")
    List<Object[]> sumPurchasesGroupedByDate(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Page<Purchase> findByPurchaseDateBetween(LocalDate fromDate, LocalDate toDate, Pageable pageable);

    Page<Purchase> findByPurchaseDateGreaterThanEqual(LocalDate fromDate, Pageable pageable);

    Page<Purchase> findByPurchaseDateLessThanEqual(LocalDate toDate, Pageable pageable);

    Page<Purchase> findAllByOrderByPurchaseDateDesc(Pageable pageable);
}
