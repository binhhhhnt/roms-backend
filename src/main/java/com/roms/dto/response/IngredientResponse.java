package com.roms.dto.response;

import com.roms.enums.StockStatus;
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
public class IngredientResponse {

    private Long id;
    private String name;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal minimumStock;
    private Boolean isActive;

    /**
     * Derived from currentStock vs minimumStock. Never persisted.
     * NORMAL | LOW | OUT_OF_STOCK
     */
    private StockStatus stockStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
