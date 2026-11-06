package com.roms.dto.response;

import java.util.List;

public record MenuExcelDuplicateConfirmation(int duplicateCount, int itemsToCreate, int categoriesToCreate,
                                             List<Duplicate> duplicates, String confirmationToken) {
    public record Duplicate(int row, String name, String category, String source, Integer originalRow) {}
}
