package com.roms.repository;

import com.roms.entity.Order;
import com.roms.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByServiceSessionId(Long serviceSessionId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByServiceSessionIdAndStatus(Long serviceSessionId, OrderStatus status);

    @Query("SELECT o FROM Order o WHERE (:sessionId IS NULL OR o.serviceSession.id = :sessionId) AND (:status IS NULL OR o.status = :status) ORDER BY o.createdAt DESC")
    List<Order> findOrdersFiltered(@Param("sessionId") Long sessionId, @Param("status") OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
