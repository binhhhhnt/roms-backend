package com.roms.service.impl;

import com.roms.dto.response.ExcelImportResultResponse;
import com.roms.entity.Category;
import com.roms.entity.MenuItem;
import com.roms.entity.TaxCategory;
import com.roms.enums.MenuItemStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.service.MenuItemExcelService;
import com.roms.service.TaxCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuItemExcelServiceImpl implements MenuItemExcelService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final TaxCategoryService taxCategoryService;

    private static final String[] HEADERS = {
            "Tên món (*)",
            "Tên danh mục (*)",
            "Giá niêm yết (*)",
            "Thuế suất VAT (%)",
            "Mô tả",
            "Trạng thái",
            "Món nổi bật",
            "Hàng bán trực tiếp"
    };

    @Override
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Menu_Template");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle exampleStyle = workbook.createCellStyle();
            exampleStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            exampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Row 0: Headers
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Row 1: Example 1 (Món chế biến qua bếp)
            Row ex1 = sheet.createRow(1);
            ex1.createCell(0).setCellValue("Phở bò đặc biệt");
            ex1.createCell(1).setCellValue("Món chính");
            ex1.createCell(2).setCellValue(85000);
            ex1.createCell(3).setCellValue(8.00);
            ex1.createCell(4).setCellValue("Phở bò tái nạm bắp hoa");
            ex1.createCell(5).setCellValue("AVAILABLE");
            ex1.createCell(6).setCellValue("CÓ");
            ex1.createCell(7).setCellValue("KHÔNG");

            // Row 2: Example 2 (Hàng bán trực tiếp: bia, rượu, nước ngọt)
            Row ex2 = sheet.createRow(2);
            ex2.createCell(0).setCellValue("Bia Heineken lon");
            ex2.createCell(1).setCellValue("Đồ uống");
            ex2.createCell(2).setCellValue(30000);
            ex2.createCell(3).setCellValue(8.00);
            ex2.createCell(4).setCellValue("Bia lon 330ml ướp lạnh");
            ex2.createCell(5).setCellValue("AVAILABLE");
            ex2.createCell(6).setCellValue("KHÔNG");
            ex2.createCell(7).setCellValue("CÓ");

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate menu template Excel", e);
            throw new BusinessRuleException("EXCEL_GENERATE_ERROR", "Không thể tạo file mẫu Excel thực đơn: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportMenuItems() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Menu_Items");

            CellStyle headerStyle = createHeaderStyle(workbook);

            // Row 0: Headers
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            List<MenuItem> items = menuItemRepository.findAllWithCategoryAndTaxCategory();
            int rowIdx = 1;
            for (MenuItem item : items) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getName());
                row.createCell(1).setCellValue(item.getCategory() != null ? item.getCategory().getName() : "");
                row.createCell(2).setCellValue(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
                row.createCell(3).setCellValue(item.getTaxCategory() != null && item.getTaxCategory().getRate() != null
                        ? item.getTaxCategory().getRate().doubleValue() : 8.00);
                row.createCell(4).setCellValue(item.getDescription() != null ? item.getDescription() : "");
                row.createCell(5).setCellValue(item.getStatus() != null ? item.getStatus().name() : MenuItemStatus.AVAILABLE.name());
                row.createCell(6).setCellValue(Boolean.TRUE.equals(item.getIsFeatured()) ? "CÓ" : "KHÔNG");
                row.createCell(7).setCellValue(Boolean.TRUE.equals(item.getIsDirectSale()) ? "CÓ" : "KHÔNG");
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export menu items to Excel", e);
            throw new BusinessRuleException("EXCEL_EXPORT_ERROR", "Không thể xuất file Excel thực đơn: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ExcelImportResultResponse importMenuItems(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("INVALID_FILE", "Vui lòng chọn file Excel hợp lệ");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls")) {
            throw new BusinessRuleException("INVALID_FILE_FORMAT", "Định dạng file không được hỗ trợ. Vui lòng tải lên file .xlsx hoặc .xls");
        }

        int totalRows = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessRuleException("EMPTY_SHEET", "File Excel không có trang tính nào");
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRowNumber = i + 1; // 1-based row in Excel UI

                try {
                    // 1. Tên món (*)
                    String name = getCellStringValue(row.getCell(0));
                    if (name == null || name.isBlank()) {
                        errors.add("Dòng " + displayRowNumber + ": Tên món ăn không được để trống");
                        continue;
                    }
                    name = name.trim();

                    // 2. Danh mục (*)
                    String categoryName = getCellStringValue(row.getCell(1));
                    if (categoryName == null || categoryName.isBlank()) {
                        errors.add("Dòng " + displayRowNumber + ": Tên danh mục không được để trống");
                        continue;
                    }
                    categoryName = categoryName.trim();

                    // 3. Giá niêm yết (*)
                    BigDecimal price = getCellBigDecimalValue(row.getCell(2));
                    if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Dòng " + displayRowNumber + ": Giá niêm yết không hợp lệ (phải là số >= 0)");
                        continue;
                    }

                    // 4. Thuế suất VAT (%)
                    BigDecimal vatRate = getCellBigDecimalValue(row.getCell(3));
                    if (vatRate == null) {
                        vatRate = BigDecimal.valueOf(8.00);
                    } else if (vatRate.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Dòng " + displayRowNumber + ": Thuế suất VAT không được âm");
                        continue;
                    }
                    vatRate = vatRate.setScale(2, RoundingMode.HALF_UP);

                    // 5. Mô tả
                    String description = getCellStringValue(row.getCell(4));

                    // 6. Trạng thái
                    String statusStr = getCellStringValue(row.getCell(5));
                    MenuItemStatus status = MenuItemStatus.AVAILABLE;
                    if (statusStr != null && !statusStr.isBlank()) {
                        try {
                            status = MenuItemStatus.valueOf(statusStr.trim().toUpperCase());
                        } catch (IllegalArgumentException ex) {
                            status = MenuItemStatus.AVAILABLE;
                        }
                    }

                    // 7. Món nổi bật
                    String featuredStr = getCellStringValue(row.getCell(6));
                    boolean isFeatured = false;
                    if (featuredStr != null) {
                        String clean = featuredStr.trim().toUpperCase();
                        isFeatured = clean.equals("CÓ") || clean.equals("TRUE") || clean.equals("YES") || clean.equals("1");
                    }

                    // 8. Hàng bán trực tiếp
                    String directSaleStr = getCellStringValue(row.getCell(7));
                    boolean isDirectSale = false;
                    if (directSaleStr != null) {
                        String clean = directSaleStr.trim().toUpperCase();
                        isDirectSale = clean.equals("CÓ") || clean.equals("TRUE") || clean.equals("YES") || clean.equals("1");
                    }

                    // Resolve or auto-create Category
                    String finalCatName = categoryName;
                    Category category = categoryRepository.findByName(finalCatName)
                            .orElseGet(() -> categoryRepository.save(Category.builder()
                                    .name(finalCatName)
                                    .displayOrder(0)
                                    .isActive(true)
                                    .build()));

                    // Resolve or auto-create TaxCategory
                    TaxCategory taxCategory = taxCategoryService.findOrCreateByRate(vatRate);

                    // Option A: Update if duplicate name, otherwise insert
                    Optional<MenuItem> existingItemOpt = menuItemRepository.findByName(name);
                    if (existingItemOpt.isPresent()) {
                        MenuItem existing = existingItemOpt.get();
                        existing.setCategory(category);
                        existing.setPrice(price);
                        existing.setTaxCategory(taxCategory);
                        existing.setDescription(description != null ? description.trim() : null);
                        existing.setStatus(status);
                        existing.setIsFeatured(isFeatured);
                        existing.setIsDirectSale(isDirectSale);
                        menuItemRepository.save(existing);
                        updatedCount++;
                    } else {
                        MenuItem newItem = MenuItem.builder()
                                .name(name)
                                .category(category)
                                .price(price)
                                .taxCategory(taxCategory)
                                .description(description != null ? description.trim() : null)
                                .status(status)
                                .isFeatured(isFeatured)
                                .isDirectSale(isDirectSale)
                                .build();
                        menuItemRepository.save(newItem);
                        insertedCount++;
                    }

                } catch (Exception ex) {
                    log.warn("Error importing menu item at row {}: {}", displayRowNumber, ex.getMessage());
                    errors.add("Dòng " + displayRowNumber + ": Lỗi xử lý (" + ex.getMessage() + ")");
                }
            }

        } catch (BusinessRuleException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Failed to parse menu items Excel file", e);
            throw new BusinessRuleException("EXCEL_PARSE_ERROR", "Lỗi đọc file Excel: " + e.getMessage());
        }

        return ExcelImportResultResponse.builder()
                .totalRows(totalRows)
                .insertedCount(insertedCount)
                .updatedCount(updatedCount)
                .errorCount(errors.size())
                .errors(errors)
                .build();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellStringValue(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    yield String.valueOf((long) val);
                } else {
                    yield String.valueOf(val);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    private BigDecimal getCellBigDecimalValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                String str = cell.getStringCellValue().trim().replace(",", "");
                if (str.isEmpty()) yield null;
                try {
                    yield new BigDecimal(str);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}

