package com.roms.dto.response;

import com.roms.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {

    private Long id;
    private Long ingredientId;
    private String ingredientName;
    private String unit;
    private StockMovementType movementType;
    private BigDecimal quantityChange;
    private BigDecimal balanceAfter;
    private Long purchaseItemId;
    private Long orderItemId;
    private Long inventoryCheckItemId;
    private String note;
    private LocalDateTime occurredAt;
}
