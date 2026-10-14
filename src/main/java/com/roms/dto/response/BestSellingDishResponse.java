package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BestSellingDishResponse {

    private Long menuItemId;
    private String menuItemName;
    private Long quantitySold;
    private BigDecimal totalRevenue;
}
