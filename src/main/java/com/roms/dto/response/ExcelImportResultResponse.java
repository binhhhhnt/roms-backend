package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResultResponse {
    private int totalRows;
    private int insertedCount;
    private int updatedCount;
    private int errorCount;
    private List<String> errors;
}

