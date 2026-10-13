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
public class OrderStatusChangedEvent {
    private Long orderId;
    private Long sessionId;
    private Long tableId;
    private OrderStatus oldStatus;
    private OrderStatus newStatus;
    private LocalDateTime occurredAt;
}
