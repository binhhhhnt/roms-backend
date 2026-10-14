package com.roms.common;

import com.roms.dto.response.DailyRevenueExpenseResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvExportUtil {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvExportUtil() {
    }

    public static byte[] generateDailyFinancialReportCsv(List<DailyRevenueExpenseResponse> records) {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("Ngày,Doanh thu (VND),Chi phí vận hành (VND),Chi phí nhập hàng (VND),Lợi nhuận ước tính (VND)\n");

        if (records != null) {
            for (DailyRevenueExpenseResponse row : records) {
                sb.append(row.getDate() != null ? row.getDate().toString() : "").append(",");
                sb.append(formatAmount(row.getRevenue())).append(",");
                sb.append(formatAmount(row.getExpenses())).append(",");
                sb.append(formatAmount(row.getPurchases())).append(",");
                sb.append(formatAmount(row.getNetProfit())).append("\n");
            }
        }

        byte[] contentBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);

        return result;
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
