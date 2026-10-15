package com.roms.service.impl;

import com.roms.common.CsvExportUtil;
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
import com.roms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseRepository purchaseRepository;
    private final ServiceSessionRepository sessionRepository;
    private final IngredientRepository ingredientRepository;

    public DashboardServiceImpl(
            OrderItemRepository orderItemRepository,
            OrderRepository orderRepository,
            ExpenseRepository expenseRepository,
            PurchaseRepository purchaseRepository,
            ServiceSessionRepository sessionRepository,
            IngredientRepository ingredientRepository) {
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.expenseRepository = expenseRepository;
        this.purchaseRepository = purchaseRepository;
        this.sessionRepository = sessionRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveDateRange(startDate, endDate);

        BigDecimal totalRevenue = orderItemRepository.sumRevenueBetweenDates(range.startDateTime(), range.endDateTime());
        BigDecimal totalExpenses = expenseRepository.sumActiveExpensesBetweenDates(range.startDate(), range.endDate());
        BigDecimal totalPurchases = purchaseRepository.sumPurchasesBetweenDates(range.startDate(), range.endDate());

        totalRevenue = (totalRevenue != null) ? totalRevenue : BigDecimal.ZERO;
        totalExpenses = (totalExpenses != null) ? totalExpenses : BigDecimal.ZERO;
        totalPurchases = (totalPurchases != null) ? totalPurchases : BigDecimal.ZERO;

        BigDecimal netProfit = totalRevenue.subtract(totalExpenses).subtract(totalPurchases);

        long completedOrders = orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.COMPLETED, range.startDateTime(), range.endDateTime());
        long cancelledOrders = orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.CANCELLED, range.startDateTime(), range.endDateTime());
        long activeSessions = sessionRepository.countByStatus(ServiceSessionStatus.ACTIVE);
        long lowStockCount = ingredientRepository.countLowStockIngredients();

        return DashboardOverviewResponse.builder()
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .totalPurchases(totalPurchases)
                .netProfit(netProfit)
                .completedOrdersCount(completedOrders)
                .cancelledOrdersCount(cancelledOrders)
                .activeSessionsCount(activeSessions)
                .lowStockIngredientsCount(lowStockCount)
                .startDate(range.startDate())
                .endDate(range.endDate())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BestSellingDishResponse> getBestSellingDishes(LocalDate startDate, LocalDate endDate, int limit) {
        DateRange range = resolveDateRange(startDate, endDate);
        int effectiveLimit = (limit <= 0 || limit > 100) ? 5 : limit;
        Pageable pageable = PageRequest.of(0, effectiveLimit);

        List<Object[]> rows = orderItemRepository.findTopSellingDishes(range.startDateTime(), range.endDateTime(), pageable);
        List<BestSellingDishResponse> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long menuItemId = (Long) row[0];
            String menuItemName = (String) row[1];
            Long quantitySold = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal totalRevenue = row[3] instanceof BigDecimal bd ? bd : (row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO);

            result.add(BestSellingDishResponse.builder()
                    .menuItemId(menuItemId)
                    .menuItemName(menuItemName)
                    .quantitySold(quantitySold)
                    .totalRevenue(totalRevenue)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyRevenueExpenseResponse> getDailyTrends(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveDateRange(startDate, endDate);

        List<Object[]> revenueRows = orderItemRepository.sumRevenueGroupedByDate(range.startDateTime(), range.endDateTime());
        List<Object[]> expenseRows = expenseRepository.sumActiveExpensesGroupedByDate(range.startDate(), range.endDate());
        List<Object[]> purchaseRows = purchaseRepository.sumPurchasesGroupedByDate(range.startDate(), range.endDate());

        Map<LocalDate, BigDecimal> revenueMap = toDateMap(revenueRows);
        Map<LocalDate, BigDecimal> expenseMap = toDateMap(expenseRows);
        Map<LocalDate, BigDecimal> purchaseMap = toDateMap(purchaseRows);

        List<DailyRevenueExpenseResponse> dailyList = new ArrayList<>();
        LocalDate current = range.startDate();

        while (!current.isAfter(range.endDate())) {
            BigDecimal rev = revenueMap.getOrDefault(current, BigDecimal.ZERO);
            BigDecimal exp = expenseMap.getOrDefault(current, BigDecimal.ZERO);
            BigDecimal pur = purchaseMap.getOrDefault(current, BigDecimal.ZERO);
            BigDecimal profit = rev.subtract(exp).subtract(pur);

            dailyList.add(DailyRevenueExpenseResponse.builder()
                    .date(current)
                    .revenue(rev)
                    .expenses(exp)
                    .purchases(pur)
                    .netProfit(profit)
                    .build());

            current = current.plusDays(1);
        }

        return dailyList;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDailyReportCsv(LocalDate startDate, LocalDate endDate) {
        List<DailyRevenueExpenseResponse> trends = getDailyTrends(startDate, endDate);
        return CsvExportUtil.generateDailyFinancialReportCsv(trends);
    }

    private Map<LocalDate, BigDecimal> toDateMap(List<Object[]> rows) {
        Map<LocalDate, BigDecimal> map = new HashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row[0] != null && row[1] != null) {
                    LocalDate date;
                    if (row[0] instanceof LocalDate ld) {
                        date = ld;
                    } else if (row[0] instanceof java.sql.Date sqlDate) {
                        date = sqlDate.toLocalDate();
                    } else {
                        date = LocalDate.parse(row[0].toString());
                    }
                    BigDecimal amount = row[1] instanceof BigDecimal bd ? bd : new BigDecimal(row[1].toString());
                    map.put(date, amount);
                }
            }
        }
        return map;
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : end.minusDays(30);

        if (start.isAfter(end)) {
            throw new BusinessRuleException("startDate cannot be after endDate");
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        return new DateRange(start, end, startDateTime, endDateTime);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate, LocalDateTime startDateTime, LocalDateTime endDateTime) {
    }
}
