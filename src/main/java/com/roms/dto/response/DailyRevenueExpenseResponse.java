package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueExpenseResponse {

    private LocalDate date;
    private BigDecimal revenue;
    private BigDecimal expenses;
    private BigDecimal purchases;
    private BigDecimal netProfit;
}
