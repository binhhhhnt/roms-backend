package com.roms.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillPreviewItemDto {
    private Long orderItemId;
    private Long menuItemId;
    private String itemName;
    private Integer quantity; // effectiveQuantity
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private Boolean isVatInclusive;
    private BigDecimal subtotal; // unitPrice * quantity
}

