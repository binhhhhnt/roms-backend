package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TableTransferHistoryResponse {

    public TableTransferHistoryResponse() {
    }

    public TableTransferHistoryResponse(Long id, Long serviceSessionId, Long fromTableId,
                                        String fromTableCode, Long toTableId, String toTableCode,
                                        Long transferredById, String transferredByName,
                                        LocalDateTime transferredAt) {
        this.id = id;
        this.serviceSessionId = serviceSessionId;
        this.fromTableId = fromTableId;
        this.fromTableCode = fromTableCode;
        this.toTableId = toTableId;
        this.toTableCode = toTableCode;
        this.transferredById = transferredById;
        this.transferredByName = transferredByName;
        this.transferredAt = transferredAt;
    }

    private Long id;
    private Long serviceSessionId;
    private Long fromTableId;
    private String fromTableCode;
    private Long toTableId;
    private String toTableCode;
    private Long transferredById;
    private String transferredByName;
    private LocalDateTime transferredAt;
}
