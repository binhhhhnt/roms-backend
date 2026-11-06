package com.roms.dto.response;

import com.roms.enums.OrderItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectSaleItemDto {
    private Long orderItemId;
    private Long orderId;
    private Long menuItemId;
    private String menuItemName;
    private Integer orderedQuantity;
    private Integer returnedQuantity;
    private Integer usedQuantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private OrderItemStatus status;
    private LocalDateTime orderCreatedAt;
}

