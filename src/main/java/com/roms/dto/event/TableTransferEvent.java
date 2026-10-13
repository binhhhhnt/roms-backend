package com.roms.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableTransferEvent {
    private Long transferHistoryId;
    private Long sessionId;
    private Long fromTableId;
    private String fromTableName;
    private Long toTableId;
    private String toTableName;
    private LocalDateTime occurredAt;
}
