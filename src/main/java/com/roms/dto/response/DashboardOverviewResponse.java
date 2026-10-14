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
public class DashboardOverviewResponse {

    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal totalPurchases;
    private BigDecimal netProfit;
    private long completedOrdersCount;
    private long cancelledOrdersCount;
    private long activeSessionsCount;
    private long lowStockIngredientsCount;
    private LocalDate startDate;
    private LocalDate endDate;
}
