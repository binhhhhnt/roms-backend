package com.roms.dto.response;

public record MenuExcelImportError(String sheet, Integer row, Integer column, String message) {
    public String formatted() {
        return sheet + (row == null ? "" : ", dòng " + row)
                + (column == null ? "" : ", cột " + column) + ": " + message;
    }
}
