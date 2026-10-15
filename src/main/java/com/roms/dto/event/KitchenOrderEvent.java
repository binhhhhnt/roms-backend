package com.roms.dto.event;

import com.roms.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KitchenOrderEvent {

    public KitchenOrderEvent() {
    }

    public KitchenOrderEvent(Long orderId, Long sessionId, Long tableId, String tableName,
                             OrderStatus status, int totalItems, LocalDateTime occurredAt) {
        this.orderId = orderId;
        this.sessionId = sessionId;
        this.tableId = tableId;
        this.tableName = tableName;
        this.status = status;
        this.totalItems = totalItems;
        this.occurredAt = occurredAt;
    }
    private Long orderId;
    private Long sessionId;
    private Long tableId;
    private String tableName;
    private OrderStatus status;
    private int totalItems;
    private LocalDateTime occurredAt;
}
