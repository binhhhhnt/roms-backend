package com.roms.repository;

import com.roms.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCodeAndIsDeletedFalse(String code);

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.isDeleted = false " +
           "AND (p.startDate IS NULL OR p.startDate <= :today) " +
           "AND (p.endDate IS NULL OR p.endDate >= :today) " +
           "ORDER BY p.createdAt DESC")
    List<Promotion> findValidPromotions(@Param("today") LocalDate today);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.promotion.id = :promotionId")
    long countUsedBills(@Param("promotionId") Long promotionId);
}

