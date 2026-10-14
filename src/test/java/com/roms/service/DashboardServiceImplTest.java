package com.roms.service;

import com.roms.dto.response.BestSellingDishResponse;
import com.roms.dto.response.DailyRevenueExpenseResponse;
import com.roms.dto.response.DashboardOverviewResponse;
import com.roms.enums.OrderStatus;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.ExpenseRepository;
import com.roms.repository.IngredientRepository;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.OrderRepository;
import com.roms.repository.PurchaseRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ServiceSessionRepository sessionRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    @DisplayName("getOverview - success calculates revenue, costs and net profit")
    void getOverview_success() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 15);

        when(orderItemRepository.sumRevenueBetweenDates(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("50000000.00"));
        when(expenseRepository.sumActiveExpensesBetweenDates(eq(start), eq(end)))
                .thenReturn(new BigDecimal("10000000.00"));
        when(purchaseRepository.sumPurchasesBetweenDates(eq(start), eq(end)))
                .thenReturn(new BigDecimal("15000000.00"));
        when(orderRepository.countByStatusAndCreatedAtBetween(eq(OrderStatus.COMPLETED), any(), any()))
                .thenReturn(120L);
        when(orderRepository.countByStatusAndCreatedAtBetween(eq(OrderStatus.CANCELLED), any(), any()))
                .thenReturn(5L);
        when(sessionRepository.countByStatus(ServiceSessionStatus.ACTIVE))
                .thenReturn(8L);
        when(ingredientRepository.countLowStockIngredients())
                .thenReturn(2L);

        DashboardOverviewResponse overview = dashboardService.getOverview(start, end);

        assertThat(overview).isNotNull();
        assertThat(overview.getTotalRevenue()).isEqualByComparingTo("50000000.00");
        assertThat(overview.getTotalExpenses()).isEqualByComparingTo("10000000.00");
        assertThat(overview.getTotalPurchases()).isEqualByComparingTo("15000000.00");
        // Net profit = 50M - 10M - 15M = 25M
        assertThat(overview.getNetProfit()).isEqualByComparingTo("25000000.00");
        assertThat(overview.getCompletedOrdersCount()).isEqualTo(120L);
        assertThat(overview.getCancelledOrdersCount()).isEqualTo(5L);
        assertThat(overview.getActiveSessionsCount()).isEqualTo(8L);
        assertThat(overview.getLowStockIngredientsCount()).isEqualTo(2L);
        assertThat(overview.getStartDate()).isEqualTo(start);
        assertThat(overview.getEndDate()).isEqualTo(end);
    }

    @Test
    @DisplayName("getOverview - invalid date range throws BusinessRuleException")
    void getOverview_invalidDateRange() {
        LocalDate start = LocalDate.of(2026, 10, 15);
        LocalDate end = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> dashboardService.getOverview(start, end))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("startDate cannot be after endDate");
    }

    @Test
    @DisplayName("getBestSellingDishes - success returns top ranked dishes")
    void getBestSellingDishes_success() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 15);

        Object[] dish1 = new Object[]{1L, "Phở Bò Tái Nạm", 85L, new BigDecimal("5525000.00")};
        Object[] dish2 = new Object[]{2L, "Cơm Tấm Sườn Bì Chả", 60L, new BigDecimal("3300000.00")};

        when(orderItemRepository.findTopSellingDishes(any(), any(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(dish1, dish2));

        List<BestSellingDishResponse> dishes = dashboardService.getBestSellingDishes(start, end, 5);

        assertThat(dishes).hasSize(2);
        assertThat(dishes.get(0).getMenuItemName()).isEqualTo("Phở Bò Tái Nạm");
        assertThat(dishes.get(0).getQuantitySold()).isEqualTo(85L);
        assertThat(dishes.get(0).getTotalRevenue()).isEqualByComparingTo("5525000.00");

        assertThat(dishes.get(1).getMenuItemName()).isEqualTo("Cơm Tấm Sườn Bì Chả");
        assertThat(dishes.get(1).getQuantitySold()).isEqualTo(60L);
    }

    @Test
    @DisplayName("getDailyTrends - success aggregates daily breakdown")
    void getDailyTrends_success() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 2);

        Object[] revDay1 = new Object[]{LocalDate.of(2026, 10, 1), new BigDecimal("1000000.00")};
        Object[] expDay1 = new Object[]{LocalDate.of(2026, 10, 1), new BigDecimal("300000.00")};
        Object[] purDay1 = new Object[]{LocalDate.of(2026, 10, 1), new BigDecimal("200000.00")};

        when(orderItemRepository.sumRevenueGroupedByDate(any(), any())).thenReturn(List.<Object[]>of(revDay1));
        when(expenseRepository.sumActiveExpensesGroupedByDate(eq(start), eq(end))).thenReturn(List.<Object[]>of(expDay1));
        when(purchaseRepository.sumPurchasesGroupedByDate(eq(start), eq(end))).thenReturn(List.<Object[]>of(purDay1));

        List<DailyRevenueExpenseResponse> trends = dashboardService.getDailyTrends(start, end);

        assertThat(trends).hasSize(2); // Oct 1 and Oct 2
        // Oct 1
        assertThat(trends.get(0).getDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(trends.get(0).getRevenue()).isEqualByComparingTo("1000000.00");
        assertThat(trends.get(0).getExpenses()).isEqualByComparingTo("300000.00");
        assertThat(trends.get(0).getPurchases()).isEqualByComparingTo("200000.00");
        assertThat(trends.get(0).getNetProfit()).isEqualByComparingTo("500000.00");

        // Oct 2 (zeros)
        assertThat(trends.get(1).getDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(trends.get(1).getRevenue()).isEqualByComparingTo("0.00");
        assertThat(trends.get(1).getNetProfit()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("exportDailyReportCsv - success generates CSV with UTF-8 BOM")
    void exportDailyReportCsv_success() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 1);

        Object[] revDay1 = new Object[]{LocalDate.of(2026, 10, 1), new BigDecimal("2000000.00")};

        when(orderItemRepository.sumRevenueGroupedByDate(any(), any())).thenReturn(List.<Object[]>of(revDay1));
        when(expenseRepository.sumActiveExpensesGroupedByDate(any(), any())).thenReturn(List.of());
        when(purchaseRepository.sumPurchasesGroupedByDate(any(), any())).thenReturn(List.of());

        byte[] csv = dashboardService.exportDailyReportCsv(start, end);

        assertThat(csv).isNotNull();
        // BOM check
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("Ngày,Doanh thu (VND),Chi phí vận hành (VND),Chi phí nhập hàng (VND),Lợi nhuận ước tính (VND)");
        assertThat(text).contains("2026-10-01,2000000.00,0.00,0.00,2000000.00");
    }
}
