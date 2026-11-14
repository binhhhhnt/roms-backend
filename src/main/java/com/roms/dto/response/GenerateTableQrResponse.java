package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTableQrResponse {

    private String restaurantName;
    private String address;
    private List<TableQrItem> tables;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableQrItem {
        private Long tableId;
        private String tableCode;
        private String area;
        private String qrToken;
    }
}
