package com.roms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MenuItemWriteRequest {
    @NotNull @Positive
    private Long categoryId;
    @NotBlank @Size(max = 255)
    private String name;
    private String description;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    private BigDecimal price;
    @NotNull
    private Boolean isFeatured;
}
