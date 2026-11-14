package com.roms.service.impl;

import com.roms.dto.response.ExcelImportResultResponse;
import com.roms.entity.Ingredient;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.IngredientRepository;
import com.roms.service.IngredientExcelService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientExcelServiceImpl implements IngredientExcelService {

    private final IngredientRepository ingredientRepository;

    private static final String[] HEADERS = {
            "Tên nguyên liệu (*)",
            "Đơn vị tính (*)",
            "Tồn kho ban đầu / hiện tại",
            "Định mức tồn tối thiểu"
    };

    @Override
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ingredient_Template");

            CellStyle headerStyle = createHeaderStyle(workbook);

            // Row 0: Headers
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Row 1: Example 1
            Row ex1 = sheet.createRow(1);
            ex1.createCell(0).setCellValue("Thịt bò thăn");
            ex1.createCell(1).setCellValue("kg");
            ex1.createCell(2).setCellValue(15.5);
            ex1.createCell(3).setCellValue(5.0);

            // Row 2: Example 2
            Row ex2 = sheet.createRow(2);
            ex2.createCell(0).setCellValue("Hành lá");
            ex2.createCell(1).setCellValue("kg");
            ex2.createCell(2).setCellValue(5.0);
            ex2.createCell(3).setCellValue(1.0);

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate ingredient template Excel", e);
            throw new BusinessRuleException("EXCEL_GENERATE_ERROR", "Failed to generate ingredient Excel template: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportIngredients() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ingredients");

            CellStyle headerStyle = createHeaderStyle(workbook);

            // Row 0: Headers
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            List<Ingredient> ingredients = ingredientRepository.findAll();
            int rowIdx = 1;
            for (Ingredient ing : ingredients) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(ing.getName());
                row.createCell(1).setCellValue(ing.getUnit());
                row.createCell(2).setCellValue(ing.getCurrentStock() != null ? ing.getCurrentStock().doubleValue() : 0.0);
                row.createCell(3).setCellValue(ing.getMinimumStock() != null ? ing.getMinimumStock().doubleValue() : 0.0);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export ingredients to Excel", e);
            throw new BusinessRuleException("EXCEL_EXPORT_ERROR", "Failed to export ingredients Excel file: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ExcelImportResultResponse importIngredients(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("INVALID_FILE", "Excel file must not be empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls")) {
            throw new BusinessRuleException("INVALID_FILE_FORMAT", "Unsupported file format; please upload .xlsx or .xls");
        }

        int totalRows = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessRuleException("EMPTY_SHEET", "Excel workbook has no sheets");
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRowNumber = i + 1;

                try {
                    // 1. Tên nguyên liệu (*)
                    String name = getCellStringValue(row.getCell(0));
                    if (name == null || name.isBlank()) {
                        errors.add("Dòng " + displayRowNumber + ": Tên nguyên liệu không được để trống");
                        continue;
                    }
                    name = name.trim();

                    // 2. Đơn vị tính (*)
                    String unit = getCellStringValue(row.getCell(1));
                    if (unit == null || unit.isBlank()) {
                        errors.add("Dòng " + displayRowNumber + ": Đơn vị tính không được để trống");
                        continue;
                    }
                    unit = unit.trim();

                    // 3. Tồn kho ban đầu / hiện tại
                    BigDecimal currentStock = getCellBigDecimalValue(row.getCell(2));
                    if (currentStock == null) {
                        currentStock = BigDecimal.ZERO;
                    } else if (currentStock.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Dòng " + displayRowNumber + ": Tồn kho không được âm");
                        continue;
                    }

                    // 4. Định mức tồn tối thiểu
                    BigDecimal minimumStock = getCellBigDecimalValue(row.getCell(3));
                    if (minimumStock == null) {
                        minimumStock = BigDecimal.ZERO;
                    } else if (minimumStock.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Dòng " + displayRowNumber + ": Định mức tồn tối thiểu không được âm");
                        continue;
                    }

                    // Option A: Update if duplicate name, otherwise insert
                    Optional<Ingredient> existingOpt = ingredientRepository.findByNameIgnoreCase(name);
                    if (existingOpt.isPresent()) {
                        Ingredient existing = existingOpt.get();
                        existing.setUnit(unit);
                        existing.setCurrentStock(currentStock);
                        existing.setMinimumStock(minimumStock);
                        existing.setIsActive(true);
                        ingredientRepository.save(existing);
                        updatedCount++;
                    } else {
                        Ingredient newIngredient = Ingredient.builder()
                                .name(name)
                                .unit(unit)
                                .currentStock(currentStock)
                                .minimumStock(minimumStock)
                                .isActive(true)
                                .build();
                        ingredientRepository.save(newIngredient);
                        insertedCount++;
                    }

                } catch (Exception ex) {
                    log.warn("Error importing ingredient at row {}: {}", displayRowNumber, ex.getMessage());
                    errors.add("Dòng " + displayRowNumber + ": Lỗi xử lý (" + ex.getMessage() + ")");
                }
            }

        } catch (BusinessRuleException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Failed to parse ingredients Excel file", e);
            throw new BusinessRuleException("EXCEL_PARSE_ERROR", "Failed to parse ingredients Excel file: " + e.getMessage());
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
