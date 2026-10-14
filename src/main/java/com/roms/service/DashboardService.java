package com.roms.service;

import com.roms.dto.response.BestSellingDishResponse;
import com.roms.dto.response.DailyRevenueExpenseResponse;
import com.roms.dto.response.DashboardOverviewResponse;

import java.time.LocalDate;
import java.util.List;

public interface DashboardService {

    DashboardOverviewResponse getOverview(LocalDate startDate, LocalDate endDate);

    List<BestSellingDishResponse> getBestSellingDishes(LocalDate startDate, LocalDate endDate, int limit);

    List<DailyRevenueExpenseResponse> getDailyTrends(LocalDate startDate, LocalDate endDate);

    byte[] exportDailyReportCsv(LocalDate startDate, LocalDate endDate);
}
