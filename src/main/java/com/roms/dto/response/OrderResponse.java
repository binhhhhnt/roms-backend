package com.roms.dto.response;

import com.roms.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private Long sessionId;
    private Long tableId;
    private String tableCode;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Long createdById;
    private String createdByName;
    private String terminationReason;
    private Long terminatedById;
    private LocalDateTime terminatedAt;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
