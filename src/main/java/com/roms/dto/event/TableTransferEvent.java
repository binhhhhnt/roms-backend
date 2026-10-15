package com.roms.dto.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TableTransferEvent {

    public TableTransferEvent() {
    }

    public TableTransferEvent(Long transferHistoryId, Long sessionId, Long fromTableId,
                              String fromTableName, Long toTableId, String toTableName,
                              LocalDateTime occurredAt) {
        this.transferHistoryId = transferHistoryId;
        this.sessionId = sessionId;
        this.fromTableId = fromTableId;
        this.fromTableName = fromTableName;
        this.toTableId = toTableId;
        this.toTableName = toTableName;
        this.occurredAt = occurredAt;
    }
    private Long transferHistoryId;
    private Long sessionId;
    private Long fromTableId;
    private String fromTableName;
    private Long toTableId;
    private String toTableName;
    private LocalDateTime occurredAt;
}
