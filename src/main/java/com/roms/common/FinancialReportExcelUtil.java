package com.roms.common;

import com.roms.dto.response.DailyRevenueExpenseResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class FinancialReportExcelUtil {

    private FinancialReportExcelUtil() {
    }

    public static byte[] generateDailyFinancialReportExcel(
            List<DailyRevenueExpenseResponse> records,
            LocalDate startDate,
            LocalDate endDate
    ) {
        DateTimeFormatter vnDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Báo cáo tài chính");

            // Colors (Navy & Sky Blue palette)
            XSSFColor navyColor = new XSSFColor(new byte[]{(byte) 0x1D, (byte) 0x35, (byte) 0x57}, null);
            XSSFColor headerColor = new XSSFColor(new byte[]{(byte) 0x2B, (byte) 0x5B, (byte) 0x84}, null);
            XSSFColor subtitleBg = new XSSFColor(new byte[]{(byte) 0xEA, (byte) 0xF2, (byte) 0xF8}, null);
            XSSFColor totalBg = new XSSFColor(new byte[]{(byte) 0xFE, (byte) 0xF3, (byte) 0xC7}, null);

            DataFormat dataFormat = workbook.createDataFormat();
            short vndFormat = dataFormat.getFormat("#,##0");

            // Title style
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(navyColor);
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Subtitle style
            XSSFCellStyle subtitleStyle = workbook.createCellStyle();
            XSSFFont subtitleFont = workbook.createFont();
            subtitleFont.setItalic(true);
            subtitleFont.setFontHeightInPoints((short) 10);
            subtitleFont.setColor(navyColor);
            subtitleStyle.setFont(subtitleFont);
            subtitleStyle.setFillForegroundColor(subtitleBg);
            subtitleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            subtitleStyle.setAlignment(HorizontalAlignment.CENTER);
            subtitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Table header style
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(headerColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(headerStyle);

            // Date cell style
            XSSFCellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            dateStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(dateStyle);

            // Money cell style
            XSSFCellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);
            moneyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            moneyStyle.setDataFormat(vndFormat);
            setThinBorders(moneyStyle);

            // Total label style
            XSSFCellStyle totalLabelStyle = workbook.createCellStyle();
            XSSFFont totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 11);
            totalLabelStyle.setFont(totalFont);
            totalLabelStyle.setFillForegroundColor(totalBg);
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(totalLabelStyle);

            // Total money style
            XSSFCellStyle totalMoneyStyle = workbook.createCellStyle();
            totalMoneyStyle.setFont(totalFont);
            totalMoneyStyle.setFillForegroundColor(totalBg);
            totalMoneyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalMoneyStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalMoneyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalMoneyStyle.setDataFormat(vndFormat);
            setThinBorders(totalMoneyStyle);

            // Row 0: Title
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(28);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO TÀI CHÍNH THEO NGÀY");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            // Row 1: Period subtitle
            Row subtitleRow = sheet.createRow(1);
            subtitleRow.setHeightInPoints(20);
            String fromStr = startDate != null ? startDate.format(vnDateFmt) : "Đầu kỳ";
            String toStr = endDate != null ? endDate.format(vnDateFmt) : LocalDate.now().format(vnDateFmt);
            Cell subCell = subtitleRow.createCell(0);
            subCell.setCellValue("Kỳ báo cáo: " + fromStr + " - " + toStr + "   |   Ngày xuất: " + LocalDate.now().format(vnDateFmt));
            subCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 4));

            // Row 2: Spacer
            sheet.createRow(2).setHeightInPoints(8);

            // Row 3: Headers
            String[] headers = {
                "Ngày",
                "Doanh thu (VND)",
                "Chi phí vận hành (VND)",
                "Chi phí nhập hàng (VND)",
                "Lợi nhuận ước tính (VND)"
            };
            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(24);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            BigDecimal totalPurchases = BigDecimal.ZERO;
            BigDecimal totalNetProfit = BigDecimal.ZERO;

            int rowIdx = 4;
            if (records != null) {
                for (DailyRevenueExpenseResponse row : records) {
                    Row dataRow = sheet.createRow(rowIdx++);
                    dataRow.setHeightInPoints(20);

                    Cell c0 = dataRow.createCell(0);
                    c0.setCellValue(row.getDate() != null ? row.getDate().format(vnDateFmt) : "");
                    c0.setCellStyle(dateStyle);

                    BigDecimal rev = safeAmount(row.getRevenue());
                    BigDecimal exp = safeAmount(row.getExpenses());
                    BigDecimal pur = safeAmount(row.getPurchases());
                    BigDecimal net = safeAmount(row.getNetProfit());

                    totalRevenue = totalRevenue.add(rev);
                    totalExpenses = totalExpenses.add(exp);
                    totalPurchases = totalPurchases.add(pur);
                    totalNetProfit = totalNetProfit.add(net);

                    setMoneyCell(dataRow, 1, rev, moneyStyle);
                    setMoneyCell(dataRow, 2, exp, moneyStyle);
                    setMoneyCell(dataRow, 3, pur, moneyStyle);
                    setMoneyCell(dataRow, 4, net, moneyStyle);
                }
            }

            // Summary row
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.setHeightInPoints(24);
            Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("Tổng cộng");
            totalLabelCell.setCellStyle(totalLabelStyle);
            setMoneyCell(totalRow, 1, totalRevenue, totalMoneyStyle);
            setMoneyCell(totalRow, 2, totalExpenses, totalMoneyStyle);
            setMoneyCell(totalRow, 3, totalPurchases, totalMoneyStyle);
            setMoneyCell(totalRow, 4, totalNetProfit, totalMoneyStyle);

            // Auto-size all columns with comfortable padding so Excel never displays ######
            for (int col = 0; col < headers.length; col++) {
                sheet.autoSizeColumn(col);
                int padded = Math.max(sheet.getColumnWidth(col) + 1200, 4200);
                sheet.setColumnWidth(col, Math.min(padded, 255 * 256));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate financial Excel report", ex);
        }
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private static void setMoneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
