package com.roms.dto.response;

import com.roms.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderResponse {

    private Long id;
    private Long sessionId;
    private Long tableId;
    private String tableCode;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
