package com.roms.service;

import com.roms.controller.MenuExcelController;
import com.roms.dto.response.MenuExcelImportError;
import com.roms.dto.response.MenuExcelDuplicateConfirmation;
import com.roms.entity.Category;
import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.UserRole;
import com.roms.exception.*;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.MenuContextInvalidator;
import com.roms.service.impl.MenuExcelServiceImpl;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MenuExcelServiceTest {
    private CategoryRepository categories;
    private MenuItemRepository items;
    private MenuContextInvalidator invalidator;
    private MenuExcelService service;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        categories = mock(CategoryRepository.class);
        items = mock(MenuItemRepository.class);
        invalidator = mock(MenuContextInvalidator.class);
        service = new MenuExcelServiceImpl(categories, items, invalidator);
        mvc = MockMvcBuilders.standaloneSetup(new MenuExcelController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate(UserRole.OWNER);
        when(categories.findAll()).thenReturn(List.of());
        when(categories.saveAll(any())).thenAnswer(call -> {
            List<Category> values = call.getArgument(0);
            for (int i = 0; i < values.size(); i++) values.get(i).setId(100L + i);
            return values;
        });
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authenticate(UserRole role) {
        var principal = UserPrincipal.builder().id(1L).username("test").active(true).role(role).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private MockMultipartFile file(Consumer<Workbook> edit) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.template())); var output = new ByteArrayOutputStream()) {
            for (var sheet : workbook) {
                for (int i = sheet.getLastRowNum(); i > 1; i--) sheet.removeRow(sheet.getRow(i));
            }
            edit.accept(workbook);
            workbook.write(output);
            return new MockMultipartFile("file", "menu.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private void noWrites() {
        verify(categories, never()).saveAll(any());
        verify(items, never()).saveAll(any());
        verifyNoInteractions(invalidator);
    }

    private MenuExcelDuplicateConfirmation reviewFor(MockMultipartFile upload) {
        var failure = catchThrowable(() -> service.importMenu(upload));
        assertThat(failure).isInstanceOf(BusinessRuleException.class);
        var conflict = (BusinessRuleException) failure;
        assertThat(conflict.getCode()).isEqualTo("EXCEL_DUPLICATES_CONFIRMATION_REQUIRED");
        assertThat(conflict.getDetails()).isInstanceOf(MenuExcelDuplicateConfirmation.class);
        noWrites();
        return (MenuExcelDuplicateConfirmation) conflict.getDetails();
    }

    @Test void templateHasExactlyTwoSheetsAndApprovedColumnsAndNumericPrices() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.template()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("Danh mục");
            assertThat(workbook.getSheetName(1)).isEqualTo("Món ăn");
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 3);
            assertThat(workbook.getSheetAt(1).getRow(0).getLastCellNum()).isEqualTo((short) 5);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(6);
            assertThat(workbook.getSheetAt(1).getLastRowNum()).isEqualTo(24);
            assertThat(workbook.getSheetAt(1).getRow(0).getCell(4).getStringCellValue()).isEqualTo("Giá");
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(4).getNumericCellValue()).isEqualTo(55000);
            assertThat(workbook.getSheetAt(0).getPaneInformation().isFreezePane()).isTrue();
            var preview = service.preview();
            for (int s = 0; s < 2; s++) for (int r = 0; r < preview.sheets().get(s).rows().size(); r++) {
                var source = preview.sheets().get(s).rows().get(r);
                for (int c = 0; c < source.size(); c++) {
                    var cell = workbook.getSheetAt(s).getRow(r + 1).getCell(c);
                    if (source.get(c) instanceof Number number) assertThat(cell.getNumericCellValue()).isEqualTo(number.doubleValue());
                    else assertThat(cell.getStringCellValue()).isEqualTo(source.get(c));
                }
            }
        }
        verifyNoInteractions(categories, items, invalidator);
    }

    @Test void importsNewCategoriesAndMapsItemsToPersistedCategory() throws Exception {
        var result = service.importMenu(file(w -> {}));
        assertThat(result.categoriesCreated()).isEqualTo(1);
        assertThat(result.itemsCreated()).isEqualTo(1);
        verify(items).saveAll(argThat(values -> {
            MenuItem value = values.iterator().next();
            return value.getCategory().getId().equals(100L) && value.getName().equals("Nem rán")
                    && value.getPrice().intValueExact() == 55000 && value.getStatus() == MenuItemStatus.AVAILABLE
                    && !value.getIsFeatured();
        }));
        verify(invalidator).afterCommit();
    }

    @Test void newCategoriesAppendAfterMaximumExistingOrderInPhysicalRowOrder() throws Exception {
        var old = Category.builder().id(5L).name("Danh mục cũ").displayOrder(50).isActive(false).build();
        when(categories.findAll()).thenReturn(List.of(old));
        var upload = file(w -> {
            var sheet = w.getSheetAt(0);
            sheet.getRow(1).getCell(0).setCellValue(99);
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("Món chính");
        });
        service.importMenu(upload);
        verify(categories).saveAll(argThat(values -> {
            var iterator = values.iterator();
            var first = iterator.next();
            var second = iterator.next();
            return first.getName().equals("Khai vị") && first.getDisplayOrder() == 51
                    && second.getName().equals("Món chính") && second.getDisplayOrder() == 52 && !iterator.hasNext();
        }));
        assertThat(old.getDisplayOrder()).isEqualTo(50);
    }

    @Test void reusedCategoryKeepsItsPositionAndNewCategoryGoesAfterExistingMaximum() throws Exception {
        var reused = Category.builder().id(7L).name("Khai vị").displayOrder(3).build();
        when(categories.findAll()).thenReturn(List.of(reused, Category.builder().id(8L).name("Khác").displayOrder(20).build()));
        var upload = file(w -> {
            var row = w.getSheetAt(0).createRow(2);
            row.createCell(0).setCellValue(2);
            row.createCell(1).setCellValue("Món chính");
        });
        service.importMenu(upload);
        assertThat(reused.getDisplayOrder()).isEqualTo(3);
        verify(categories).saveAll(argThat(values -> {
            var iterator = values.iterator();
            var value = iterator.next();
            return value.getName().equals("Món chính") && value.getDisplayOrder() == 21 && !iterator.hasNext();
        }));
    }

    @Test void dishesAreSavedInRowOrderRatherThanSerialOrAlphabeticalOrder() throws Exception {
        var upload = file(w -> {
            var sheet = w.getSheetAt(1);
            sheet.getRow(1).getCell(0).setCellValue(99);
            sheet.getRow(1).getCell(1).setCellValue("Z Món trước");
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("A Món sau");
            row.createCell(2).setCellValue("Khai vị");
            row.createCell(4).setCellValue(10000);
        });
        service.importMenu(upload);
        verify(items).saveAll(argThat(values -> {
            var iterator = values.iterator();
            return iterator.next().getName().equals("Z Món trước") && iterator.next().getName().equals("A Món sau") && !iterator.hasNext();
        }));
    }

    @Test void categoryOrderOverflowRejectsImportBeforeWriting() throws Exception {
        when(categories.findAll()).thenReturn(List.of(Category.builder().id(7L).name("Khác").displayOrder(Integer.MAX_VALUE).build()));
        var upload = file(w -> {});
        assertThatThrownBy(() -> service.importMenu(upload)).isInstanceOf(ValidationException.class).hasMessageContaining("giới hạn");
        noWrites();
    }

    @Test void reusesUniqueDatabaseCategoryWithCaseAndWhitespaceNormalization() throws Exception {
        when(categories.findAll()).thenReturn(List.of(Category.builder().id(7L).name("  KHAI   VỊ ").build()));
        var result = service.importMenu(file(w -> {}));
        assertThat(result.categoriesCreated()).isZero();
        assertThat(result.categoriesReused()).isEqualTo(1);
        verify(items).saveAll(argThat(values -> values.iterator().next().getCategory().getId().equals(7L)));
    }

    @Test void resolvesExistingCategoryEvenWhenCategorySheetOnlyContainsHeaders() throws Exception {
        when(categories.findAll()).thenReturn(List.of(Category.builder().id(7L).name("Khai vị").build()));
        var result = service.importMenu(file(w -> w.getSheetAt(0).removeRow(w.getSheetAt(0).getRow(1))));
        assertThat(result.categoriesReused()).isEqualTo(1);
        assertThat(result.itemsCreated()).isEqualTo(1);
    }

    @Test void duplicateDatabaseCategoriesAreRejectedBeforeAnyWrites() throws Exception {
        when(categories.findAll()).thenReturn(List.of(Category.builder().id(7L).name("Khai vị").build(), Category.builder().id(8L).name("khai vị").build()));
        var file = file(w -> {});
        assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class).satisfies(ex ->
                assertThat(((ValidationException) ex).getDetails().toString()).contains("nhiều danh mục"));
        noWrites();
    }

    @Test void missingCategoryDoesNotCreatePartialCategoryData() throws Exception {
        var file = file(w -> w.getSheetAt(1).getRow(1).getCell(2).setCellValue("Không tồn tại"));
        assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class).hasMessageContaining("dòng 2").hasMessageContaining("Không tìm thấy danh mục");
        noWrites();
    }

    @Test void duplicateFileCategoryNamesAreRejected() throws Exception {
        var file = file(w -> {
            var row = w.getSheetAt(0).createRow(2);
            row.createCell(0).setCellValue(2);
            row.createCell(1).setCellValue(" KHAI VỊ ");
        });
        assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class).hasMessageContaining("bị trùng");
        noWrites();
    }

    @Test void duplicateItemsInWorkbookAreSkipped() throws Exception {
        var file = file(w -> {
            var sheet = w.getSheetAt(1);
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue(2);
            row.createCell(1).setCellValue("Nem rán");
            row.createCell(2).setCellValue("Khai vị");
            row.createCell(4).setCellValue(55000);
        });
        var review = reviewFor(file);
        assertThat(review.categoriesToCreate()).isEqualTo(1);
        assertThat(review.duplicates()).singleElement().satisfies(value -> {
            assertThat(value.source()).isEqualTo("FILE");
            assertThat(value.row()).isEqualTo(3);
            assertThat(value.originalRow()).isEqualTo(2);
        });
        var result = service.importMenu(file, review.confirmationToken());
        assertThat(result.itemsCreated()).isEqualTo(1);
        assertThat(result.itemsSkipped()).isEqualTo(1);
    }

    @Test void existingItemIsSkippedWithoutOverwritingOnRepeatUpload() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        when(categories.findAll()).thenReturn(List.of(category));
        var original = MenuItem.builder().category(category).name(" NEM RÁN ").price(new java.math.BigDecimal("49000")).build();
        when(items.findAllForExcelImport()).thenReturn(List.of(original));
        var file = file(w -> {});
        var review = reviewFor(file);
        assertThat(review.itemsToCreate()).isZero();
        assertThat(review.duplicates()).singleElement().satisfies(value -> {
            assertThat(value.name()).isEqualTo("Nem rán");
            assertThat(value.category()).isEqualTo("Khai vị");
            assertThat(value.source()).isEqualTo("CATALOG");
        });
        var result = service.importMenu(file, review.confirmationToken());
        assertThat(result.itemsCreated()).isZero();
        assertThat(result.itemsSkipped()).isEqualTo(1);
        assertThat(original.getPrice()).isEqualByComparingTo("49000");
        noWrites();
    }

    @Test void skipsExistingItemButAddsNewItemsInTheSameUpload() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        when(categories.findAll()).thenReturn(List.of(category));
        when(items.findAllForExcelImport()).thenReturn(List.of(MenuItem.builder().category(category).name("Nem rán").build()));
        var upload = file(w -> addFood(w, 2, 2, "Salad", "Khai vị", 35000));
        var review = reviewFor(upload);
        assertThat(review.duplicateCount()).isEqualTo(1);
        assertThat(review.itemsToCreate()).isEqualTo(1);
        var result = service.importMenu(upload, review.confirmationToken());
        assertThat(result.itemsCreated()).isEqualTo(1);
        assertThat(result.itemsSkipped()).isEqualTo(1);
        verify(items).saveAll(argThat(values -> values.iterator().next().getName().equals("Salad")));
    }

    @Test void listsEveryDuplicateFromCatalogAndFileBeforeSaving() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        when(categories.findAll()).thenReturn(List.of(category));
        when(items.findAllForExcelImport()).thenReturn(List.of(MenuItem.builder().category(category).name("Nem rán").build()));
        var upload = file(w -> {
            addFood(w, 2, 2, "Salad", "Khai vị", 35000);
            addFood(w, 3, 3, " salad ", " KHAI VỊ ", 40000);
        });
        var review = reviewFor(upload);
        assertThat(review.duplicateCount()).isEqualTo(2);
        assertThat(review.itemsToCreate()).isEqualTo(1);
        assertThat(review.duplicates()).extracting(MenuExcelDuplicateConfirmation.Duplicate::row).containsExactly(2, 4);
        assertThat(review.duplicates()).extracting(MenuExcelDuplicateConfirmation.Duplicate::source).containsExactly("CATALOG", "FILE");
        var result = service.importMenu(upload, review.confirmationToken());
        assertThat(result.itemsCreated()).isEqualTo(1);
        assertThat(result.itemsSkipped()).isEqualTo(2);
    }

    @Test void reviewTokenCannotApproveAnotherFileOrAnArbitraryConfirmation() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        when(categories.findAll()).thenReturn(List.of(category));
        when(items.findAllForExcelImport()).thenReturn(List.of(MenuItem.builder().category(category).name("Nem rán").build()));
        var upload = file(w -> addFood(w, 2, 2, "Salad", "Khai vị", 35000));
        var review = reviewFor(upload);
        var changed = file(w -> addFood(w, 2, 2, "Gỏi cuốn", "Khai vị", 35000));
        for (var token : List.of(review.confirmationToken(), "true")) {
            assertThatThrownBy(() -> service.importMenu(changed, token)).isInstanceOf(BusinessRuleException.class).satisfies(ex -> {
                var refreshed = (MenuExcelDuplicateConfirmation) ((BusinessRuleException) ex).getDetails();
                assertThat(refreshed.confirmationToken()).isNotEqualTo(review.confirmationToken());
            });
        }
        noWrites();
    }

    @Test void changedDuplicateListRequiresFreshApproval() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        var original = MenuItem.builder().category(category).name("Nem rán").build();
        when(categories.findAll()).thenReturn(List.of(category));
        when(items.findAllForExcelImport()).thenReturn(List.of(original));
        var upload = file(w -> addFood(w, 2, 2, "Salad", "Khai vị", 35000));
        var review = reviewFor(upload);
        when(items.findAllForExcelImport()).thenReturn(List.of(original, MenuItem.builder().category(category).name("Salad").build()));
        assertThatThrownBy(() -> service.importMenu(upload, review.confirmationToken())).isInstanceOf(BusinessRuleException.class).satisfies(ex -> {
            var refreshed = (MenuExcelDuplicateConfirmation) ((BusinessRuleException) ex).getDetails();
            assertThat(refreshed.duplicateCount()).isEqualTo(2);
            assertThat(refreshed.itemsToCreate()).isZero();
            assertThat(refreshed.confirmationToken()).isNotEqualTo(review.confirmationToken());
        });
        noWrites();
    }

    @Test void errorsTakePriorityAndConfirmationCannotBypassNewConflicts() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        var original = MenuItem.builder().category(category).name("Nem rán").build();
        when(categories.findAll()).thenReturn(List.of(category));
        when(items.findAllForExcelImport()).thenReturn(List.of(original));
        var upload = file(w -> addFood(w, 2, 2, "Salad", "Khai vị", 35000));
        var review = reviewFor(upload);
        var other = Category.builder().id(9L).name("Món chính").build();
        when(items.findAllForExcelImport()).thenReturn(List.of(original, MenuItem.builder().category(other).name("Salad").build()));
        assertThatThrownBy(() -> service.importMenu(upload, review.confirmationToken())).isInstanceOf(ValidationException.class)
                .hasMessageContaining("khác danh mục");
        noWrites();
    }

    @Test void httpDuplicateResponseListsRowsAndRequiresTheReturnedTokenToSave() throws Exception {
        var upload = file(w -> addFood(w, 2, 2, "Nem rán", "Khai vị", 55000));
        var review = reviewFor(upload);
        mvc.perform(multipart("/api/menu-excel/import").file(upload)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXCEL_DUPLICATES_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.details.duplicateCount").value(1))
                .andExpect(jsonPath("$.error.details.itemsToCreate").value(1))
                .andExpect(jsonPath("$.error.details.duplicates[0].row").value(3))
                .andExpect(jsonPath("$.error.details.duplicates[0].name").value("Nem rán"))
                .andExpect(jsonPath("$.error.details.duplicates[0].category").value("Khai vị"));
        noWrites();
        mvc.perform(multipart("/api/menu-excel/import").file(upload).param("confirmationToken", review.confirmationToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.itemsCreated").value(1))
                .andExpect(jsonPath("$.data.itemsSkipped").value(1));
    }

    @Test void sameNameInAnotherDatabaseCategoryIsAnErrorEvenIfThatCategoryIsNotInTheFile() throws Exception {
        var other = Category.builder().id(9L).name("Món chính").build();
        when(items.findAllForExcelImport()).thenReturn(List.of(MenuItem.builder().category(other).name(" NEM RÁN ").build()));
        var upload = file(w -> {});
        assertThatThrownBy(() -> service.importMenu(upload)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("trùng tên nhưng khác danh mục").hasMessageContaining("Món chính").hasMessageContaining("Khai vị");
        noWrites();
    }

    @Test void exactExistingPairIsSkippedAndOnlyTheWrongCategoryRowIsReported() throws Exception {
        var category = Category.builder().id(7L).name("Khai vị").build();
        var other = Category.builder().id(9L).name("Món chính").build();
        when(categories.findAll()).thenReturn(List.of(category, other));
        when(items.findAllForExcelImport()).thenReturn(List.of(MenuItem.builder().category(category).name("Nem rán").build()));
        var upload = file(w -> addFood(w, 2, 2, "Nem rán", "Món chính", 55000));
        assertThatThrownBy(() -> service.importMenu(upload)).isInstanceOf(ValidationException.class).satisfies(ex -> {
            var errors = (List<?>) ((ValidationException) ex).getDetails();
            assertThat(errors).singleElement().satisfies(error -> assertThat(((MenuExcelImportError) error).row()).isEqualTo(3));
        });
        noWrites();
    }

    @Test void sameNameAcrossTwoFileCategoriesReportsBothConflictingRows() throws Exception {
        var upload = file(w -> {
            var category = w.getSheetAt(0).createRow(2);
            category.createCell(0).setCellValue(2);
            category.createCell(1).setCellValue("Món chính");
            addFood(w, 2, 2, "nem rán", "Món chính", 55000);
        });
        assertThatThrownBy(() -> service.importMenu(upload)).isInstanceOf(ValidationException.class).satisfies(ex -> {
            var errors = (List<?>) ((ValidationException) ex).getDetails();
            assertThat(errors).hasSize(2);
            assertThat(errors.toString()).contains("row=2", "row=3", "trùng tên nhưng khác danh mục");
        });
        noWrites();
    }

    @Test void collectsCellErrorsAcrossRowsAndSheetsAndDatabaseConflictsTogether() throws Exception {
        var other = Category.builder().id(9L).name("Món chính").build();
        when(items.findAllForExcelImport()).thenReturn(List.of(MenuItem.builder().category(other).name("Nem rán").build()));
        var upload = file(w -> {
            w.getSheetAt(0).getRow(1).getCell(2).setCellValue("a".repeat(256));
            w.getSheetAt(1).getRow(1).getCell(4).setCellValue(-1);
            addFood(w, 2, 2, "Salad", "Không tồn tại", -1);
        });
        assertThatThrownBy(() -> service.importMenu(upload)).isInstanceOf(ValidationException.class).satisfies(ex -> {
            var errors = (List<?>) ((ValidationException) ex).getDetails();
            assertThat(errors).hasSize(5);
            assertThat(errors.toString()).contains("sheet=Danh mục", "row=2", "row=3", "Không tìm thấy danh mục", "khác danh mục", "Giá phải");
        });
        noWrites();
    }

    @Test void httpResponseIncludesAllErrorsWithSheetRowAndColumn() throws Exception {
        var upload = file(w -> {
            w.getSheetAt(1).getRow(1).getCell(1).setCellValue(123);
            w.getSheetAt(1).getRow(1).getCell(4).setCellValue(-1);
            addFood(w, 2, 2, "Salad", "Khai vị", -1);
        });
        mvc.perform(multipart("/api/menu-excel/import").file(upload)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.length()").value(3))
                .andExpect(jsonPath("$.error.details[0].sheet").value("Món ăn"))
                .andExpect(jsonPath("$.error.details[0].row").value(2))
                .andExpect(jsonPath("$.error.details[0].column").value(2))
                .andExpect(jsonPath("$.error.details[2].row").value(3));
        noWrites();
    }

    private void addFood(Workbook workbook, int rowIndex, int serial, String name, String category, double price) {
        var row = workbook.getSheetAt(1).createRow(rowIndex);
        row.createCell(0).setCellValue(serial);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(category);
        row.createCell(4).setCellValue(price);
    }

    @Test void invalidPricesAreRejectedBeforeDatabaseWrites() throws Exception {
        for (double price : new double[]{-1, 0.001, 10000000000d}) {
            var file = file(w -> w.getSheetAt(1).getRow(1).getCell(4).setCellValue(price));
            assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class).hasMessageContaining("Giá phải");
        }
        noWrites();
    }

    @Test void formulaAndExtraColumnAndExtraSheetAreRejected() throws Exception {
        List<Consumer<Workbook>> edits = List.of(
                w -> w.getSheetAt(1).getRow(1).getCell(4).setCellFormula("50000+5000"),
                w -> w.getSheetAt(1).getRow(0).createCell(5).setCellValue("Trạng thái"),
                w -> w.createSheet("Khác"));
        for (var edit : edits) {
            var file = file(edit);
            assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class);
        }
        noWrites();
    }

    @Test void invalidHeaderAndEmptyWorkbookAreRejected() throws Exception {
        var badHeader = file(w -> w.getSheetAt(1).getRow(0).getCell(2).setCellValue("Mã danh mục"));
        assertThatThrownBy(() -> service.importMenu(badHeader)).isInstanceOf(ValidationException.class).hasMessageContaining("Tên danh mục");
        var empty = file(w -> { for (var sheet : w) sheet.removeRow(sheet.getRow(1)); });
        assertThatThrownBy(() -> service.importMenu(empty)).isInstanceOf(ValidationException.class).hasMessageContaining("không có dữ liệu");
        noWrites();
    }

    @Test void legacyXlsFilesAndRenamedXlsFilesAreRejected() throws Exception {
        try (var workbook = new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            for (var source : service.preview().sheets()) {
                var sheet = workbook.createSheet(source.name());
                var header = sheet.createRow(0);
                for (int i = 0; i < source.columns().size(); i++) header.createCell(i).setCellValue(source.columns().get(i));
                var row = sheet.createRow(1);
                var values = source.rows().getFirst();
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i) instanceof Number number) row.createCell(i).setCellValue(number.doubleValue());
                    else row.createCell(i).setCellValue(values.get(i).toString());
                }
            }
            workbook.write(output);
            for (var name : List.of("menu.xls", "menu.xlsx")) {
                var file = new MockMultipartFile("file", name, "application/vnd.ms-excel", output.toByteArray());
                assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class).hasMessageContaining(".xlsx");
            }
            noWrites();
        }
    }

    @Test void nonOwnersAndUnauthenticatedUsersCannotReadTemplateOrImport() {
        for (var role : UserRole.values()) {
            if (role == UserRole.OWNER) continue;
            authenticate(role);
            assertThatThrownBy(service::preview).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(service::template).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.importMenu(null)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.importMenu(null, "confirmed")).isInstanceOf(ForbiddenException.class);
        }
        SecurityContextHolder.clearContext();
        assertThatThrownBy(service::preview).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.importMenu(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.importMenu(null, "confirmed")).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(categories, items, invalidator);
    }

    @Test void malformedAndOversizedUploadsAreRejected() {
        for (var file : List.of(new MockMultipartFile("file", "menu.xlsx", null, "not an Excel file".getBytes()),
                new MockMultipartFile("file", "menu.csv", null, new byte[]{1}),
                new MockMultipartFile("file", "menu.xlsx", null, new byte[0]),
                new MockMultipartFile("file", "menu.xlsx", null, new byte[5 * 1024 * 1024 + 1]))) {
            assertThatThrownBy(() -> service.importMenu(file)).isInstanceOf(ValidationException.class);
        }
        noWrites();
    }

    @Test void httpEndpointsServeDownloadPreviewAndMultipartImport() throws Exception {
        mvc.perform(get("/api/menu-excel/template")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Menu_mau.xlsx\""))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/api/menu-excel/template/preview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sheets[0].name").value("Danh mục"))
                .andExpect(jsonPath("$.data.sheets[1].rows.length()").value(24));
        mvc.perform(multipart("/api/menu-excel/import").file(file(w -> {}))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemsCreated").value(1));
    }

    @Test void httpImportErrorsReturnUseful400Response() throws Exception {
        mvc.perform(multipart("/api/menu-excel/import")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/menu-excel/import").file(file(w -> w.getSheetAt(1).getRow(1).getCell(4).setCellValue(-1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("dòng 2")));
        noWrites();
    }
}
