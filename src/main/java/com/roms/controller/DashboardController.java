package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.response.BestSellingDishResponse;
import com.roms.dto.response.DailyRevenueExpenseResponse;
import com.roms.dto.response.DashboardOverviewResponse;
import com.roms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        DashboardOverviewResponse response = dashboardService.getOverview(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Dashboard overview retrieved successfully", response));
    }

    @GetMapping("/best-selling")
    public ResponseEntity<ApiResponse<List<BestSellingDishResponse>>> getBestSellingDishes(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        List<BestSellingDishResponse> response = dashboardService.getBestSellingDishes(startDate, endDate, limit);
        return ResponseEntity.ok(ApiResponse.success("Best selling dishes retrieved successfully", response));
    }

    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<List<DailyRevenueExpenseResponse>>> getDailyTrends(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<DailyRevenueExpenseResponse> response = dashboardService.getDailyTrends(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Daily trends retrieved successfully", response));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDailyReportCsv(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] csvBytes = dashboardService.exportDailyReportCsv(startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"daily-financial-report-" + LocalDate.now() + ".csv\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }
}
