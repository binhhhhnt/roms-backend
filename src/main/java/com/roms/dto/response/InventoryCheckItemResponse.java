package com.roms.dto.response;

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
public class InventoryCheckItemResponse {

    private Long id;
    private Long ingredientId;
    private String ingredientName;
    private String unit;
    private BigDecimal systemQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal difference;
    private String reason;
    private LocalDateTime createdAt;
}
