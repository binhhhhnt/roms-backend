package com.roms.dto.response;

import java.util.List;

public record MenuExcelPreview(String filename, List<Sheet> sheets) {
    public record Sheet(String name, List<String> columns, List<List<Object>> rows) {}
}
