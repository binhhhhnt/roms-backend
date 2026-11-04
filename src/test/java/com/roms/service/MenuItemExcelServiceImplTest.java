package com.roms.service;

import com.roms.dto.response.ExcelImportResultResponse;
import com.roms.entity.Category;
import com.roms.entity.MenuItem;
import com.roms.entity.TaxCategory;
import com.roms.enums.MenuItemStatus;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.service.impl.MenuItemExcelServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuItemExcelServiceImplTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TaxCategoryService taxCategoryService;

    @InjectMocks
    private MenuItemExcelServiceImpl menuItemExcelService;

    private Category testCategory;
    private TaxCategory testTaxCategory;
    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Món chính")
                .displayOrder(1)
                .isActive(true)
                .build();

        testTaxCategory = TaxCategory.builder()
                .id(1L)
                .rate(BigDecimal.valueOf(8.00))
                .build();

        testMenuItem = MenuItem.builder()
                .id(1L)
                .name("Phở Bò")
                .category(testCategory)
                .price(BigDecimal.valueOf(65000))
                .taxCategory(testTaxCategory)
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(true)
                .build();
    }

    @Test
    @DisplayName("generateTemplate returns valid XLSX byte array with template headers")
    void generateTemplateSuccessfully() throws IOException {
        byte[] bytes = menuItemExcelService.generateTemplate();
        assertThat(bytes).isNotNull().isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Menu_Template");
            assertThat(sheet).isNotNull();
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).contains("Tên món");
            assertThat(headerRow.getCell(1).getStringCellValue()).contains("Tên danh mục");
            assertThat(headerRow.getCell(2).getStringCellValue()).contains("Giá niêm yết");
            assertThat(headerRow.getCell(3).getStringCellValue()).contains("Thuế suất");
        }
    }

    @Test
    @DisplayName("exportMenuItems returns valid XLSX with all menu items")
    void exportMenuItemsSuccessfully() throws IOException {
        when(menuItemRepository.findAllWithCategoryAndTaxCategory()).thenReturn(List.of(testMenuItem));

        byte[] bytes = menuItemExcelService.exportMenuItems();
        assertThat(bytes).isNotNull().isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Menu_Items");
            assertThat(sheet).isNotNull();
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("Phở Bò");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Món chính");
            assertThat(dataRow.getCell(2).getNumericCellValue()).isEqualTo(65000.0);
            assertThat(dataRow.getCell(3).getNumericCellValue()).isEqualTo(8.0);
        }
    }

    @Test
    @DisplayName("importMenuItems creates new item and updates existing item on duplicate name")
    void importMenuItemsSuccessfully() throws IOException {
        // Create an in-memory XLSX workbook
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Import");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên món (*)");
            header.createCell(1).setCellValue("Tên danh mục (*)");
            header.createCell(2).setCellValue("Giá niêm yết (*)");
            header.createCell(3).setCellValue("Thuế suất VAT (%)");
            header.createCell(4).setCellValue("Mô tả");
            header.createCell(5).setCellValue("Trạng thái");
            header.createCell(6).setCellValue("Món nổi bật");

            // Row 1: Existing item to update
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Phở Bò");
            row1.createCell(1).setCellValue("Món chính");
            row1.createCell(2).setCellValue(70000);
            row1.createCell(3).setCellValue(8.0);
            row1.createCell(4).setCellValue("Phở bò tái nạm cập nhật");
            row1.createCell(5).setCellValue("AVAILABLE");
            row1.createCell(6).setCellValue("CÓ");

            // Row 2: New item to insert
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Bún chả Hà Nội");
            row2.createCell(1).setCellValue("Món chính");
            row2.createCell(2).setCellValue(60000);
            row2.createCell(3).setCellValue(10.0);
            row2.createCell(4).setCellValue("Bún chả nướng than hoa");
            row2.createCell(5).setCellValue("AVAILABLE");
            row2.createCell(6).setCellValue("KHÔNG");

            wb.write(out);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "menu.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );

        when(categoryRepository.findByName("Món chính")).thenReturn(Optional.of(testCategory));
        when(taxCategoryService.findOrCreateByRate(any())).thenReturn(testTaxCategory);
        when(menuItemRepository.findByName("Phở Bò")).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.findByName("Bún chả Hà Nội")).thenReturn(Optional.empty());

        ExcelImportResultResponse result = menuItemExcelService.importMenuItems(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getInsertedCount()).isEqualTo(1);
        assertThat(result.getErrorCount()).isEqualTo(0);

        verify(menuItemRepository, times(2)).save(any(MenuItem.class));
    }
}

