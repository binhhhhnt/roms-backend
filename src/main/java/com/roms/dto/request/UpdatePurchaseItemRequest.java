package com.roms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class UpdatePurchaseItemRequest {

    @NotNull(message = "Quantity must not be null")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull(message = "Unit price must not be null")
    @DecimalMin(value = "0", message = "Unit price must be >= 0")
    private BigDecimal unitPrice;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;
}
