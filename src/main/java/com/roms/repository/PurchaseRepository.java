package com.roms.repository;

import com.roms.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Page<Purchase> findByPurchaseDateBetween(LocalDate fromDate, LocalDate toDate, Pageable pageable);

    Page<Purchase> findByPurchaseDateGreaterThanEqual(LocalDate fromDate, Pageable pageable);

    Page<Purchase> findByPurchaseDateLessThanEqual(LocalDate toDate, Pageable pageable);

    Page<Purchase> findAllByOrderByPurchaseDateDesc(Pageable pageable);
}
