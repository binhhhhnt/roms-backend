package com.roms.service;

import com.roms.dto.response.ExcelImportResultResponse;
import com.roms.entity.Ingredient;
import com.roms.repository.IngredientRepository;
import com.roms.service.impl.IngredientExcelServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientExcelServiceImplTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientExcelServiceImpl ingredientExcelService;

    private Ingredient testIngredient;

    @BeforeEach
    void setUp() {
        testIngredient = Ingredient.builder()
                .id(1L)
                .name("Thịt bò thăn")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(10.0))
                .minimumStock(BigDecimal.valueOf(3.0))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("generateTemplate returns valid XLSX byte array with template headers")
    void generateTemplateSuccessfully() throws IOException {
        byte[] bytes = ingredientExcelService.generateTemplate();
        assertThat(bytes).isNotNull().isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Ingredient_Template");
            assertThat(sheet).isNotNull();
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).contains("Tên nguyên liệu");
            assertThat(headerRow.getCell(1).getStringCellValue()).contains("Đơn vị tính");
            assertThat(headerRow.getCell(2).getStringCellValue()).contains("Tồn kho");
        }
    }

    @Test
    @DisplayName("exportIngredients returns valid XLSX with all ingredients")
    void exportIngredientsSuccessfully() throws IOException {
        when(ingredientRepository.findAll()).thenReturn(List.of(testIngredient));

        byte[] bytes = ingredientExcelService.exportIngredients();
        assertThat(bytes).isNotNull().isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Ingredients");
            assertThat(sheet).isNotNull();
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("Thịt bò thăn");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("kg");
            assertThat(dataRow.getCell(2).getNumericCellValue()).isEqualTo(10.0);
            assertThat(dataRow.getCell(3).getNumericCellValue()).isEqualTo(3.0);
        }
    }

    @Test
    @DisplayName("importIngredients creates new ingredient and updates existing ingredient on duplicate name")
    void importIngredientsSuccessfully() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Import");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên nguyên liệu (*)");
            header.createCell(1).setCellValue("Đơn vị tính (*)");
            header.createCell(2).setCellValue("Tồn kho ban đầu / hiện tại");
            header.createCell(3).setCellValue("Định mức tồn tối thiểu");

            // Row 1: Existing ingredient to update
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Thịt bò thăn");
            row1.createCell(1).setCellValue("kg");
            row1.createCell(2).setCellValue(20.0);
            row1.createCell(3).setCellValue(5.0);

            // Row 2: New ingredient to insert
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Hành tây");
            row2.createCell(1).setCellValue("kg");
            row2.createCell(2).setCellValue(8.0);
            row2.createCell(3).setCellValue(2.0);

            wb.write(out);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "ingredients.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );

        when(ingredientRepository.findByNameIgnoreCase("Thịt bò thăn")).thenReturn(Optional.of(testIngredient));
        when(ingredientRepository.findByNameIgnoreCase("Hành tây")).thenReturn(Optional.empty());

        ExcelImportResultResponse result = ingredientExcelService.importIngredients(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getInsertedCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(0);

        verify(ingredientRepository, times(2)).save(any(Ingredient.class));
    }
}
