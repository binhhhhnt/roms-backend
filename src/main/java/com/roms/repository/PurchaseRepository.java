package com.roms.repository;

import com.roms.entity.Purchase;
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
           "(:startDate IS NULL OR p.purchaseDate >= :startDate) AND " +
           "(:endDate IS NULL OR p.purchaseDate <= :endDate)")
    BigDecimal sumPurchasesBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT p.purchaseDate, COALESCE(SUM(p.totalAmount), 0) FROM Purchase p WHERE " +
           "(:startDate IS NULL OR p.purchaseDate >= :startDate) AND " +
           "(:endDate IS NULL OR p.purchaseDate <= :endDate) " +
           "GROUP BY p.purchaseDate ORDER BY p.purchaseDate ASC")
    List<Object[]> sumPurchasesGroupedByDate(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
