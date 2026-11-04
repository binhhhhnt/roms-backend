package com.roms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCategoryRequest {

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.00", message = "Tax rate must not be negative")
    @Digits(integer = 3, fraction = 2, message = "Tax rate format must be up to 3 integer digits and 2 decimal places")
    private BigDecimal rate;
}

