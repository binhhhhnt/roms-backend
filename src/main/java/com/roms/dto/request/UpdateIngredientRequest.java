package com.roms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class UpdateIngredientRequest {

    @NotBlank(message = "Ingredient name must not be blank")
    @Size(max = 255, message = "Ingredient name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Unit must not be blank")
    @Size(max = 20, message = "Unit must not exceed 20 characters")
    private String unit;

    /**
     * Nullable — pass null to clear the minimum stock threshold.
     */
    @DecimalMin(value = "0", message = "Minimum stock must be >= 0")
    private BigDecimal minimumStock;
}
