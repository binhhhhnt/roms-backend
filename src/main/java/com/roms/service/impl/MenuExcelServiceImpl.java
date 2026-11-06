package com.roms.service.impl;

import com.roms.dto.response.MenuExcelImportResponse;
import com.roms.dto.response.MenuExcelImportError;
import com.roms.dto.response.MenuExcelDuplicateConfirmation;
import com.roms.dto.response.MenuExcelPreview;
import com.roms.entity.Category;
import com.roms.entity.MenuItem;
import com.roms.exception.ValidationException;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.service.MenuExcelService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class MenuExcelServiceImpl implements MenuExcelService {
    private static final int MAX_ROWS = 5000;
    private static final List<String> CATEGORY_COLUMNS = List.of("STT", "Tên danh mục", "Mô tả");
    private static final List<String> ITEM_COLUMNS = List.of("STT", "Tên món", "Tên danh mục", "Mô tả", "Giá");
    private final CategoryRepository categories;
    private final MenuItemRepository items;
    private final MenuContextInvalidator invalidator;

    @Override
    public MenuExcelPreview preview() {
        CatalogAccess.requireOwner();
        var categoryRows = new ArrayList<List<Object>>();
        String[][] categoryData = {
                {"Khai vị", "Các món nhẹ dùng trước bữa ăn"}, {"Món chính", "Các món chính trong bữa ăn"},
                {"Món nước", "Phở, bún và các món mì"}, {"Rau và món phụ", "Rau, đậu phụ và cơm ăn kèm"},
                {"Tráng miệng", "Món ngọt và trái cây"}, {"Đồ uống", "Trà, cà phê và nước giải khát"}
        };
        for (int i = 0; i < categoryData.length; i++) {
            categoryRows.add(List.of(i + 1, categoryData[i][0], categoryData[i][1]));
        }
        String[][] data = {
                {"Nem rán", "0", "Nem rán giòn, ăn kèm rau sống", "55000"},
                {"Gỏi cuốn tôm thịt", "0", "Gỏi cuốn với tôm, thịt và rau", "45000"},
                {"Salad rau củ", "0", "Rau củ tươi trộn sốt", "49000"},
                {"Khoai tây chiên", "0", "Khoai tây chiên giòn", "39000"},
                {"Cơm rang hải sản", "1", "Cơm rang tôm, mực", "89000"},
                {"Bò lúc lắc", "1", "Thịt bò xào rau củ", "129000"},
                {"Gà nướng mật ong", "1", "Gà nướng sốt mật ong", "119000"},
                {"Sườn nướng", "1", "Sườn heo nướng", "139000"},
                {"Phở bò", "2", "Phở bò nước dùng truyền thống", "65000"},
                {"Bún bò Huế", "2", "Bún bò nước dùng đậm vị", "70000"},
                {"Bún chả", "2", "Bún ăn cùng chả nướng", "65000"},
                {"Mì xào hải sản", "2", "Mì xào tôm mực và rau", "85000"},
                {"Rau muống xào tỏi", "3", "Rau muống xào tỏi", "45000"},
                {"Cải thìa xào nấm", "3", "Cải thìa xào nấm", "49000"},
                {"Đậu phụ sốt cà chua", "3", "Đậu phụ sốt cà chua", "45000"},
                {"Cơm trắng", "3", "Cơm trắng nóng", "15000"},
                {"Chè khúc bạch", "4", "Chè khúc bạch và trái cây", "39000"},
                {"Sữa chua trái cây", "4", "Sữa chua cùng trái cây", "35000"},
                {"Kem vani", "4", "Kem vani", "29000"},
                {"Hoa quả theo mùa", "4", "Trái cây tươi theo mùa", "45000"},
                {"Trà đào", "5", "Trà đào mát lạnh", "35000"},
                {"Nước cam", "5", "Nước cam tươi", "39000"},
                {"Cà phê sữa", "5", "Cà phê pha sữa", "30000"},
                {"Nước suối", "5", "Nước uống đóng chai", "15000"}
        };
        var itemRows = new ArrayList<List<Object>>();
        for (int i = 0; i < data.length; i++) {
            var d = data[i];
            itemRows.add(List.of(i + 1, d[0], categoryData[Integer.parseInt(d[1])][0], d[2], new BigDecimal(d[3])));
        }
        return new MenuExcelPreview("Menu_mau.xlsx", List.of(
                new MenuExcelPreview.Sheet("Danh mục", CATEGORY_COLUMNS, categoryRows),
                new MenuExcelPreview.Sheet("Món ăn", ITEM_COLUMNS, itemRows)));
    }

    @Override
    public byte[] template() {
        var preview = preview();
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var header = workbook.createCellStyle();
            header.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{29, 53, 87}, null));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            var price = workbook.createCellStyle();
            price.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
            for (var source : preview.sheets()) {
                var sheet = workbook.createSheet(source.name());
                var heading = sheet.createRow(0);
                heading.setHeightInPoints(25);
                for (int c = 0; c < source.columns().size(); c++) {
                    var cell = heading.createCell(c);
                    cell.setCellValue(source.columns().get(c));
                    cell.setCellStyle(header);
                    sheet.setColumnWidth(c, (c == 0 ? 8 : c == source.columns().size() - 1 && source.name().equals("Món ăn") ? 18 : 36) * 256);
                }
                for (int r = 0; r < source.rows().size(); r++) {
                    var row = sheet.createRow(r + 1);
                    var values = source.rows().get(r);
                    for (int c = 0; c < values.size(); c++) {
                        var cell = row.createCell(c);
                        if (values.get(c) instanceof Number number) cell.setCellValue(number.doubleValue());
                        else cell.setCellValue(values.get(c).toString());
                        if (source.name().equals("Món ăn") && c == 4) cell.setCellStyle(price);
                    }
                }
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new CellRangeAddress(0, source.rows().size(), 0, source.columns().size() - 1));
            }
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo file Excel mẫu", ex);
        }
    }

    private record CategoryRow(String name, String description, int row) {}
    private record ItemRow(String name, String category, String description, BigDecimal price, int row) {}

    @Override
    @Transactional
    public MenuExcelImportResponse importMenu(MultipartFile file) {
        return importMenu(file, null);
    }

    @Override
    @Transactional
    public MenuExcelImportResponse importMenu(MultipartFile file, String confirmationToken) {
        CatalogAccess.requireOwner();
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ValidationException("Chọn file Excel .xlsx không rỗng, tối đa 5 MB.");
        }
        // Parse and validate every row before any database write.
        List<CategoryRow> categoryRows = new ArrayList<>();
        List<ItemRow> itemRows = new ArrayList<>();
        List<MenuExcelImportError> errors = new ArrayList<>();
        try (var input = file.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (!(workbook instanceof XSSFWorkbook)) throw new ValidationException("File phải có định dạng Excel .xlsx.");
            if (workbook.getNumberOfSheets() != 2 || workbook.getSheet("Danh mục") == null || workbook.getSheet("Món ăn") == null) {
                errors.add(new MenuExcelImportError("File", null, null, "File phải có đúng hai sheet: Danh mục và Món ăn."));
            }
            var categorySheet = workbook.getSheet("Danh mục");
            var categoryNames = new HashSet<String>();
            var serials = new HashSet<Integer>();
            if (categorySheet != null && validateSheet(categorySheet, CATEGORY_COLUMNS, errors)) for (var row : categorySheet) {
                if (row.getRowNum() == 0 || blank(row)) continue;
                collect(errors, row, 0, () -> { validateSerial(row, serials); return true; });
                String name = collect(errors, row, 1, () -> text(row, 1, true, 100));
                String description = collect(errors, row, 2, () -> text(row, 2, false, 255));
                if (name == null) continue;
                if (!categoryNames.add(key(name))) {
                    errors.add(new MenuExcelImportError("Danh mục", row.getRowNum() + 1, 2, "Tên danh mục bị trùng: " + name));
                    continue;
                }
                categoryRows.add(new CategoryRow(name, description, row.getRowNum() + 1));
            }
            var itemSheet = workbook.getSheet("Món ăn");
            serials.clear();
            if (itemSheet != null && validateSheet(itemSheet, ITEM_COLUMNS, errors)) for (var row : itemSheet) {
                if (row.getRowNum() == 0 || blank(row)) continue;
                collect(errors, row, 0, () -> { validateSerial(row, serials); return true; });
                String name = collect(errors, row, 1, () -> text(row, 1, true, 255));
                String category = collect(errors, row, 2, () -> text(row, 2, true, 100));
                String description = collect(errors, row, 3, () -> text(row, 3, false, 10000));
                BigDecimal price = collect(errors, row, 4, () -> {
                    var value = decimal(row, 4);
                    if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2 || value.compareTo(new BigDecimal("9999999999.99")) > 0) {
                        fail(row, "Giá phải từ 0 đến 9.999.999.999,99 và tối đa 2 chữ số thập phân.");
                    }
                    return value;
                });
                itemRows.add(new ItemRow(name, category, description, price, row.getRowNum() + 1));
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ValidationException("Không đọc được file Excel. Hãy dùng file mẫu .xlsx không có mật khẩu.");
        }
        if (categoryRows.isEmpty() && itemRows.isEmpty() && errors.isEmpty()) throw new ValidationException("File Excel không có dữ liệu để nhập.");

        var existingByName = new HashMap<String, List<Category>>();
        var existingCategories = categories.findAll();
        long nextOrder = (long) existingCategories.stream().map(Category::getDisplayOrder)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (var category : existingCategories) {
            existingByName.computeIfAbsent(key(category.getName()), ignored -> new ArrayList<>()).add(category);
        }
        var resolved = new LinkedHashMap<String, Category>();
        var newCategories = new ArrayList<Category>();
        int reused = 0;
        for (var row : categoryRows) {
            String name = key(row.name());
            var matches = existingByName.getOrDefault(name, List.of());
            if (matches.size() > 1) {
                errors.add(new MenuExcelImportError("Danh mục", row.row(), 2, "Database có nhiều danh mục cùng tên: " + row.name()));
                continue;
            }
            if (matches.size() == 1) {
                resolved.put(name, matches.getFirst());
                reused++;
            } else {
                if (nextOrder > Integer.MAX_VALUE) {
                    errors.add(new MenuExcelImportError("Danh mục", row.row(), 2, "Thứ tự danh mục đã đạt giới hạn, không thể thêm danh mục mới."));
                    continue;
                }
                var category = Category.builder().name(row.name()).description(row.description()).displayOrder((int) nextOrder++).build();
                resolved.put(name, category);
                newCategories.add(category);
            }
        }
        var declaredNames = new HashSet<>(categoryRows.stream().map(row -> key(row.name())).toList());
        for (var row : itemRows) {
            if (row.category() == null) continue;
            String name = key(row.category());
            if (resolved.containsKey(name)) continue;
            var matches = existingByName.getOrDefault(name, List.of());
            if (matches.isEmpty() && declaredNames.contains(name)) continue;
            if (matches.size() != 1) {
                errors.add(new MenuExcelImportError("Món ăn", row.row(), 3,
                        (matches.isEmpty() ? "Không tìm thấy danh mục: " : "Database có nhiều danh mục cùng tên: ") + row.category()));
                continue;
            }
            resolved.put(name, matches.getFirst());
            reused++;
        }
        // Compare names across the entire catalog, including categories absent from the upload.
        var existingPairs = new HashSet<String>();
        var categoriesByItem = new HashMap<String, Map<String, String>>();
        for (var item : items.findAllForExcelImport()) {
            String categoryName = item.getCategory().getName();
            categoriesByItem.computeIfAbsent(key(item.getName()), ignored -> new LinkedHashMap<>())
                    .put(key(categoryName), categoryName);
            existingPairs.add(key(categoryName) + "\u0000" + key(item.getName()));
        }
        for (var row : itemRows) {
            if (row.name() == null || row.category() == null) continue;
            categoriesByItem.computeIfAbsent(key(row.name()), ignored -> new LinkedHashMap<>())
                    .put(key(row.category()), row.category());
        }
        var accepted = new ArrayList<ItemRow>();
        var acceptedPairs = new HashMap<String, Integer>();
        var duplicates = new ArrayList<MenuExcelDuplicateConfirmation.Duplicate>();
        for (var row : itemRows) {
            if (row.name() == null || row.category() == null) continue;
            String pair = key(row.category()) + "\u0000" + key(row.name());
            var destinations = categoriesByItem.get(key(row.name()));
            if (existingPairs.contains(pair)) {
                duplicates.add(new MenuExcelDuplicateConfirmation.Duplicate(row.row(), row.name(), row.category(), "CATALOG", null));
            } else if (destinations.size() > 1) {
                errors.add(new MenuExcelImportError("Món ăn", row.row(), 3,
                        "Món “" + row.name() + "” trùng tên nhưng khác danh mục: " + String.join(", ", destinations.values()) + "."));
            } else if (acceptedPairs.containsKey(pair)) {
                duplicates.add(new MenuExcelDuplicateConfirmation.Duplicate(row.row(), row.name(), row.category(), "FILE", acceptedPairs.get(pair)));
            } else {
                acceptedPairs.put(pair, row.row());
                accepted.add(row);
            }
        }
        rejectErrors(errors);
        if (!duplicates.isEmpty()) {
            String expectedToken = confirmationToken(file, duplicates, accepted.size(), newCategories.size());
            if (!expectedToken.equals(confirmationToken)) {
                var review = new MenuExcelDuplicateConfirmation(duplicates.size(), accepted.size(), newCategories.size(), List.copyOf(duplicates), expectedToken);
                throw new BusinessRuleException("EXCEL_DUPLICATES_CONFIRMATION_REQUIRED",
                        "Có " + duplicates.size() + " món trùng. Vui lòng xác nhận trước khi nhập.", review);
            }
        }
        if (!newCategories.isEmpty()) categories.saveAll(newCategories);
        var newItems = accepted.stream().map(row -> MenuItem.builder().name(row.name()).description(row.description())
                .category(resolved.get(key(row.category()))).price(row.price()).build()).toList();
        if (!newItems.isEmpty()) items.saveAll(newItems);
        if (!newCategories.isEmpty() || !newItems.isEmpty()) invalidator.afterCommit();
        return new MenuExcelImportResponse(newCategories.size(), reused, newItems.size(), duplicates.size());
    }

    private static String confirmationToken(MultipartFile file, List<MenuExcelDuplicateConfirmation.Duplicate> duplicates,
                                             int newItems, int newCategories) {
        try {
            // Bind approval to the exact file and the current duplicate list and import counts.
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.getBytes());
            digest.update(("\u0000" + duplicates + "\u0000" + newItems + "\u0000" + newCategories).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new ValidationException("Không đọc được file Excel. Vui lòng chọn lại file.");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static <T> T collect(List<MenuExcelImportError> errors, Row row, int column, Supplier<T> check) {
        try {
            return check.get();
        } catch (ValidationException ex) {
            String prefix = row.getSheet().getSheetName() + ", dòng " + (row.getRowNum() + 1) + ": ";
            String message = ex.getMessage().startsWith(prefix) ? ex.getMessage().substring(prefix.length()) : ex.getMessage();
            errors.add(new MenuExcelImportError(row.getSheet().getSheetName(), row.getRowNum() + 1, column + 1, message));
            return null;
        }
    }

    private static void rejectErrors(List<MenuExcelImportError> errors) {
        if (!errors.isEmpty()) throw new ValidationException(errors.size() == 1 ? errors.getFirst().formatted()
                : "File Excel có " + errors.size() + " lỗi. Chưa nhập dữ liệu.", List.copyOf(errors));
    }

    private static boolean validateSheet(Sheet sheet, List<String> columns, List<MenuExcelImportError> errors) {
        if (sheet.getLastRowNum() > MAX_ROWS) {
            errors.add(new MenuExcelImportError(sheet.getSheetName(), null, null, "Tối đa " + MAX_ROWS + " dòng dữ liệu."));
            return false;
        }
        var header = sheet.getRow(0);
        if (header == null) errors.add(new MenuExcelImportError(sheet.getSheetName(), 1, null, "Thiếu hàng tiêu đề."));
        else for (int c = 0; c < columns.size(); c++) {
            final int column = c;
            collect(errors, header, c, () -> {
                if (!text(header, column, true, 100).equals(columns.get(column))) fail(header, "Cột " + (column + 1) + " phải là " + columns.get(column));
                return true;
            });
        }
        for (var row : sheet) for (var cell : row) {
            if (cell.getColumnIndex() >= columns.size() && cell.getCellType() != CellType.BLANK
                    && !new DataFormatter().formatCellValue(cell).isBlank()) {
                errors.add(new MenuExcelImportError(sheet.getSheetName(), row.getRowNum() + 1, cell.getColumnIndex() + 1, "Có cột ngoài cấu trúc file mẫu."));
            }
        }
        return true;
    }

    private static boolean blank(Row row) {
        for (var cell : row) if (cell.getCellType() != CellType.BLANK && !new DataFormatter().formatCellValue(cell).isBlank()) return false;
        return true;
    }

    private static String text(Row row, int column, boolean required, int max) {
        var cell = row.getCell(column);
        if (cell != null && cell.getCellType() == CellType.FORMULA) fail(row, "Không hỗ trợ ô công thức; hãy nhập giá trị trực tiếp.");
        if (cell != null && cell.getCellType() != CellType.STRING && cell.getCellType() != CellType.BLANK) {
            fail(row, "Cột " + (column + 1) + " phải là văn bản.");
        }
        String value = cell == null ? "" : cell.getStringCellValue().strip();
        if ((required && key(value).isBlank()) || value.length() > max) fail(row, "Cột " + (column + 1) + " không được rỗng và tối đa " + max + " ký tự.");
        return value;
    }

    private static BigDecimal decimal(Row row, int column) {
        var cell = row.getCell(column);
        if (cell != null && cell.getCellType() == CellType.FORMULA) fail(row, "Không hỗ trợ ô công thức; hãy nhập giá trị trực tiếp.");
        try {
            if (cell != null && cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell != null && cell.getCellType() == CellType.STRING && cell.getStringCellValue().strip().matches("[0-9]+([.,][0-9]{1,2})?")) {
                return new BigDecimal(cell.getStringCellValue().strip().replace(',', '.'));
            }
        } catch (NumberFormatException ignored) { /* Report the sheet and row below. */ }
        fail(row, "Cột " + (column + 1) + " phải là số hợp lệ, không dùng dấu phân cách hàng nghìn.");
        throw new IllegalStateException("Unreachable");
    }

    private static void validateSerial(Row row, Set<Integer> serials) {
        var serial = decimal(row, 0);
        try {
            int value = serial.intValueExact();
            if (value <= 0 || !serials.add(value)) fail(row, "STT phải là số nguyên dương và không trùng.");
        } catch (ArithmeticException ex) {
            fail(row, "STT phải là số nguyên dương.");
        }
    }

    private static void fail(Row row, String message) {
        throw new ValidationException(row.getSheet().getSheetName() + ", dòng " + (row.getRowNum() + 1) + ": " + message);
    }
}
