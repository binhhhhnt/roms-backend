package com.roms.controller;

import com.roms.config.SecurityConfig;
import com.roms.dto.response.BestSellingDishResponse;
import com.roms.dto.response.DailyRevenueExpenseResponse;
import com.roms.dto.response.DashboardOverviewResponse;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.security.JwtAuthenticationFilter;
import com.roms.service.DashboardService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/dashboard/overview - success as OWNER returns 200 OK")
    @WithMockUser(roles = "OWNER")
    void getOverview_owner_success() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 15);

        DashboardOverviewResponse response = DashboardOverviewResponse.builder()
                .startDate(start)
                .endDate(end)
                .totalRevenue(new BigDecimal("50000000.00"))
                .totalExpenses(new BigDecimal("10000000.00"))
                .totalPurchases(new BigDecimal("15000000.00"))
                .netProfit(new BigDecimal("25000000.00"))
                .completedOrdersCount(120L)
                .cancelledOrdersCount(5L)
                .activeSessionsCount(8L)
                .lowStockIngredientsCount(2L)
                .build();

        when(dashboardService.getOverview(eq(start), eq(end))).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/overview")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dashboard overview retrieved successfully"))
                .andExpect(jsonPath("$.data.totalRevenue").value(50000000.00))
                .andExpect(jsonPath("$.data.totalExpenses").value(10000000.00))
                .andExpect(jsonPath("$.data.totalPurchases").value(15000000.00))
                .andExpect(jsonPath("$.data.netProfit").value(25000000.00))
                .andExpect(jsonPath("$.data.completedOrdersCount").value(120))
                .andExpect(jsonPath("$.data.activeSessionsCount").value(8))
                .andExpect(jsonPath("$.data.lowStockIngredientsCount").value(2));
    }

    @Test
    @DisplayName("GET /api/dashboard/overview - forbidden for non-OWNER returns 403")
    @WithMockUser(roles = "WAITER")
    void getOverview_waiter_forbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/dashboard/overview - unauthenticated returns 401")
    void getOverview_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/overview - invalid date range returns 409 Conflict")
    @WithMockUser(roles = "OWNER")
    void getOverview_invalidDateRange_returns409() throws Exception {
        when(dashboardService.getOverview(any(), any()))
                .thenThrow(new BusinessRuleException("startDate cannot be after endDate"));

        mockMvc.perform(get("/api/dashboard/overview")
                        .param("startDate", "2026-10-15")
                        .param("endDate", "2026-10-01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("startDate cannot be after endDate"));
    }

    @Test
    @DisplayName("GET /api/dashboard/best-selling - success returns 200 OK")
    @WithMockUser(roles = "OWNER")
    void getBestSellingDishes_success() throws Exception {
        List<BestSellingDishResponse> dishes = List.of(
                BestSellingDishResponse.builder()
                        .menuItemId(1L)
                        .menuItemName("Phở Bò Tái Nạm")
                        .quantitySold(85L)
                        .totalRevenue(new BigDecimal("5525000.00"))
                        .build()
        );

        when(dashboardService.getBestSellingDishes(any(), any(), eq(5))).thenReturn(dishes);

        mockMvc.perform(get("/api/dashboard/best-selling")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].menuItemName").value("Phở Bò Tái Nạm"))
                .andExpect(jsonPath("$.data[0].quantitySold").value(85))
                .andExpect(jsonPath("$.data[0].totalRevenue").value(5525000.00));
    }

    @Test
    @DisplayName("GET /api/dashboard/trends - success returns 200 OK")
    @WithMockUser(roles = "OWNER")
    void getDailyTrends_success() throws Exception {
        List<DailyRevenueExpenseResponse> trends = List.of(
                DailyRevenueExpenseResponse.builder()
                        .date(LocalDate.of(2026, 10, 1))
                        .revenue(new BigDecimal("1000000.00"))
                        .expenses(new BigDecimal("300000.00"))
                        .purchases(new BigDecimal("200000.00"))
                        .netProfit(new BigDecimal("500000.00"))
                        .build()
        );

        when(dashboardService.getDailyTrends(any(), any())).thenReturn(trends);

        mockMvc.perform(get("/api/dashboard/trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.data[0].revenue").value(1000000.00))
                .andExpect(jsonPath("$.data[0].netProfit").value(500000.00));
    }

    @Test
    @DisplayName("GET /api/dashboard/export - success returns 200 OK with CSV attachment and UTF-8 BOM")
    @WithMockUser(roles = "OWNER")
    void exportDailyReportCsv_success() throws Exception {
        byte[] dummyCsv = ("\uFEFFNgày,Doanh thu (VND),Chi phí vận hành (VND),Chi phí nhập hàng (VND),Lợi nhuận ước tính (VND)\n" +
                "2026-10-01,1000000.00,300000.00,200000.00,500000.00\n").getBytes(StandardCharsets.UTF_8);

        when(dashboardService.exportDailyReportCsv(any(), any())).thenReturn(dummyCsv);

        mockMvc.perform(get("/api/dashboard/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"daily-financial-report-")))
                .andExpect(content().bytes(dummyCsv));
    }
}
