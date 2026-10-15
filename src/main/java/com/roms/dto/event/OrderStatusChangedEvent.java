package com.roms.dto.event;

import com.roms.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrderStatusChangedEvent {

    public OrderStatusChangedEvent() {
    }

    public OrderStatusChangedEvent(Long orderId, Long sessionId, Long tableId,
                                   OrderStatus oldStatus, OrderStatus newStatus,
                                   LocalDateTime occurredAt) {
        this.orderId = orderId;
        this.sessionId = sessionId;
        this.tableId = tableId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.occurredAt = occurredAt;
    }
    private Long orderId;
    private Long sessionId;
    private Long tableId;
    private OrderStatus oldStatus;
    private OrderStatus newStatus;
    private LocalDateTime occurredAt;
}
