package com.roms.repository;

import com.roms.entity.Bill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    @EntityGraph(attributePaths = {"items", "payments", "promotion", "createdBy", "serviceSession"})
    Optional<Bill> findByServiceSessionId(Long serviceSessionId);

    @EntityGraph(attributePaths = {"items", "payments", "promotion", "createdBy", "serviceSession"})
    Optional<Bill> findByBillCode(String billCode);

    @EntityGraph(attributePaths = {"items", "payments", "promotion", "createdBy", "serviceSession"})
    @Query("SELECT b FROM Bill b WHERE b.id = :id")
    Optional<Bill> findByIdWithDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"promotion", "createdBy", "serviceSession"})
    @Query("SELECT b FROM Bill b ORDER BY b.createdAt DESC")
    Page<Bill> findAllWithDetails(Pageable pageable);

    @EntityGraph(attributePaths = {"promotion", "createdBy", "serviceSession"})
    @Query("SELECT b FROM Bill b WHERE " +
           "(:search IS NULL OR LOWER(b.billCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "   OR LOWER(b.tableCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY b.createdAt DESC")
    Page<Bill> findAllFiltered(@Param("search") String search, Pageable pageable);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(bill_code, 15) AS INTEGER)), 0) " +
                   "FROM bills WHERE bill_code LIKE :datePrefix", nativeQuery = true)
    int findMaxSequenceForDate(@Param("datePrefix") String datePrefix);
}
