package com.roms.repository;

import com.roms.entity.OrderItem;
import com.roms.enums.OrderItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByStatus(OrderItemStatus status);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.menuItem WHERE oi.order.id = :orderId ORDER BY oi.id ASC")
    List<OrderItem> findByOrderIdWithMenuItem(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(oi.quantity * oi.unitPrice), 0) FROM OrderItem oi " +
           "WHERE oi.status = com.roms.enums.OrderItemStatus.COMPLETED AND oi.order.status = com.roms.enums.OrderStatus.COMPLETED " +
           "AND oi.order.createdAt BETWEEN :startDate AND :endDate")
    java.math.BigDecimal sumRevenueBetweenDates(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT CAST(oi.order.createdAt AS LocalDate), COALESCE(SUM(oi.quantity * oi.unitPrice), 0) FROM OrderItem oi " +
           "WHERE oi.status = com.roms.enums.OrderItemStatus.COMPLETED AND oi.order.status = com.roms.enums.OrderStatus.COMPLETED " +
           "AND oi.order.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY CAST(oi.order.createdAt AS LocalDate) ORDER BY CAST(oi.order.createdAt AS LocalDate) ASC")
    List<Object[]> sumRevenueGroupedByDate(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT oi.menuItem.id, oi.menuItem.name, SUM(oi.quantity), SUM(oi.quantity * oi.unitPrice) " +
           "FROM OrderItem oi WHERE oi.status = com.roms.enums.OrderItemStatus.COMPLETED AND oi.order.status = com.roms.enums.OrderStatus.COMPLETED " +
           "AND oi.order.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY oi.menuItem.id, oi.menuItem.name ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingDishes(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            org.springframework.data.domain.Pageable pageable);
}
