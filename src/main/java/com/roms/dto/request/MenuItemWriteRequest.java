package com.roms.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MenuItemWriteRequest {
    @NotNull @Positive
    private Long categoryId;

    @JsonAlias({"taxCategoryId", "tax_category_id"})
    private Long taxCategoryId;

    @NotBlank @Size(max = 255)
    private String name;

    private String description;

    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @NotNull
    private Boolean isFeatured;

    @JsonAlias({"isDirectSale", "is_direct_sale"})
    private Boolean isDirectSale;

    private com.roms.enums.MenuItemStatus status;

    public Long getTaxCategoryId() {
        return taxCategoryId;
    }

    public void setTaxCategoryId(Long taxCategoryId) {
        this.taxCategoryId = taxCategoryId;
    }
}
