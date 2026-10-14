package com.roms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCheckItemRequest {

    @NotNull(message = "Ingredient ID must not be null")
    private Long ingredientId;

    /**
     * Actual counted quantity (>= 0). The system_quantity is read from
     * ingredients.current_stock at check time; difference is computed by the service.
     */
    @NotNull(message = "Actual quantity must not be null")
    @DecimalMin(value = "0", message = "Actual quantity must be >= 0")
    private BigDecimal actualQuantity;

    /**
     * Optional explanation for the discrepancy.
     */
    private String reason;
}
