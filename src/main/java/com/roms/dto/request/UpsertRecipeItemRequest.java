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
public class UpsertRecipeItemRequest {

    @NotNull(message = "Ingredient ID must not be null")
    private Long ingredientId;

    @NotNull(message = "Quantity required must not be null")
    @DecimalMin(value = "0.001", message = "Quantity required must be greater than 0")
    private BigDecimal quantityRequired;
}
