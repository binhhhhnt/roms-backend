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
}
