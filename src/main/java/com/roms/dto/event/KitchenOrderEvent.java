package com.roms.dto.event;

import com.roms.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderEvent {
    private Long orderId;
    private Long sessionId;
    private Long tableId;
    private String tableName;
    private OrderStatus status;
    private int totalItems;
    private LocalDateTime occurredAt;
}
