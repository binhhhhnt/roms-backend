package com.roms.dto.event;

import com.roms.enums.OrderItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemStatusChangedEvent {
    private Long orderItemId;
    private Long orderId;
    private Long sessionId;
    private Long menuItemId;
    private String itemName;
    private OrderItemStatus oldStatus;
    private OrderItemStatus newStatus;
    private LocalDateTime occurredAt;
}
