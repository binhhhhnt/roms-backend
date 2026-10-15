package com.roms.dto.event;

import com.roms.enums.ServiceSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionStatusChangedEvent {

    public SessionStatusChangedEvent() {
    }

    public SessionStatusChangedEvent(Long sessionId, Long tableId, String tableName, ServiceSessionStatus status, LocalDateTime occurredAt) {
        this.sessionId = sessionId;
        this.tableId = tableId;
        this.tableName = tableName;
        this.status = status;
        this.occurredAt = occurredAt;
    }
    private Long sessionId;
    private Long tableId;
    private String tableName;
    private ServiceSessionStatus status;
    private LocalDateTime occurredAt;
}
