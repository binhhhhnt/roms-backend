package com.roms.repository;

import com.roms.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e WHERE " +
           "(:#{#startDate == null} = true OR e.expenseDate >= :startDate) AND " +
           "(:#{#endDate == null} = true OR e.expenseDate <= :endDate) AND " +
           "(:#{#status == null || #status == 'ALL'} = true OR " +
           " (:#{#status == 'ACTIVE'} = true AND e.cancelledAt IS NULL) OR " +
           " (:#{#status == 'CANCELLED'} = true AND e.cancelledAt IS NOT NULL))")
    Page<Expense> findFiltered(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE " +
           "e.cancelledAt IS NULL AND " +
           "e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal sumActiveExpensesBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT e.expenseDate, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE " +
           "e.cancelledAt IS NULL AND " +
           "e.expenseDate BETWEEN :startDate AND :endDate " +
           "GROUP BY e.expenseDate ORDER BY e.expenseDate ASC")
    java.util.List<Object[]> sumActiveExpensesGroupedByDate(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
