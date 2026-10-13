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
public class OrderItemResponse {

    private Long id;
    private Long menuItemId;
    private String menuItemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private OrderItemStatus status;
    private String note;
    private String terminationReason;
    private Long terminatedById;
    private LocalDateTime terminatedAt;
    private LocalDateTime createdAt;
}
