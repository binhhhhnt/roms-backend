package com.roms.repository;

import com.roms.entity.Order;
import com.roms.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByServiceSessionId(Long serviceSessionId);
    List<Order> findByStatus(OrderStatus status);
    boolean existsByServiceSessionIdAndStatusIn(Long serviceSessionId, Collection<OrderStatus> statuses);
}

