package com.roms.dto.event;

import com.roms.enums.OrderItemStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrderItemStatusChangedEvent {

    public OrderItemStatusChangedEvent() {
    }

    public OrderItemStatusChangedEvent(Long orderItemId, Long orderId, Long sessionId,
                                       Long menuItemId, String itemName, OrderItemStatus oldStatus,
                                       OrderItemStatus newStatus, LocalDateTime occurredAt) {
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.sessionId = sessionId;
        this.menuItemId = menuItemId;
        this.itemName = itemName;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.occurredAt = occurredAt;
    }
    private Long orderItemId;
    private Long orderId;
    private Long sessionId;
    private Long menuItemId;
    private String itemName;
    private OrderItemStatus oldStatus;
    private OrderItemStatus newStatus;
    private LocalDateTime occurredAt;
}
