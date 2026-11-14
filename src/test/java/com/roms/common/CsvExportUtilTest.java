package com.roms.common;

import com.roms.dto.response.DailyRevenueExpenseResponse;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CsvExportUtilTest {
    @Test @DisplayName("CSV: UTF-8 BOM preserves Vietnamese column headings in Excel")
    void utf8BomAndVietnameseHeader() {
        byte[] csv = CsvExportUtil.generateDailyFinancialReportCsv(List.of());
        assertThat(csv).startsWith((byte)0xEF, (byte)0xBB, (byte)0xBF);
        assertThat(new String(csv, StandardCharsets.UTF_8)).contains("Ngày,Doanh thu (VND)");
    }

    @Test @DisplayName("CSV: null records produce only the header")
    void nullRecords() {
        String csv = new String(CsvExportUtil.generateDailyFinancialReportCsv(null), StandardCharsets.UTF_8);
        assertThat(csv.lines().count()).isEqualTo(1);
    }

    @Test @DisplayName("CSV: amounts round HALF_UP to two decimal places")
    void roundingHalfUp() {
        var record = new DailyRevenueExpenseResponse(LocalDate.of(2026,11,14),
                new BigDecimal("100.005"), new BigDecimal("10.004"), new BigDecimal("20"), new BigDecimal("70.001"));
        String csv = new String(CsvExportUtil.generateDailyFinancialReportCsv(List.of(record)), StandardCharsets.UTF_8);
        assertThat(csv).endsWith("2026-11-14,100.01,10.00,20.00,70.00\n");
    }

    @Test @DisplayName("CSV: missing date is blank and missing amounts are zero")
    void nullFields() {
        String csv = new String(CsvExportUtil.generateDailyFinancialReportCsv(List.of(new DailyRevenueExpenseResponse())), StandardCharsets.UTF_8);
        assertThat(csv).endsWith(",0.00,0.00,0.00,0.00\n");
    }

    @Test @DisplayName("CSV: negative net profit is preserved and rounded")
    void negativeProfit() {
        var record = DailyRevenueExpenseResponse.builder().netProfit(new BigDecimal("-0.005")).build();
        String csv = new String(CsvExportUtil.generateDailyFinancialReportCsv(List.of(record)), StandardCharsets.UTF_8);
        assertThat(csv).endsWith(",0.00,0.00,0.00,-0.01\n");
    }

    @Test @DisplayName("CSV: retain record order and avoid scientific notation for large amounts")
    void orderedRowsAndLargeAmount() {
        var first = DailyRevenueExpenseResponse.builder().date(LocalDate.of(2026,11,13)).revenue(new BigDecimal("1E+12")).build();
        var second = DailyRevenueExpenseResponse.builder().date(LocalDate.of(2026,11,14)).build();
        String csv = new String(CsvExportUtil.generateDailyFinancialReportCsv(List.of(first,second)), StandardCharsets.UTF_8);
        assertThat(csv).contains("2026-11-13,1000000000000.00,0.00,0.00,0.00\n2026-11-14,");
        assertThat(csv.lines().count()).isEqualTo(3);
    }
}
