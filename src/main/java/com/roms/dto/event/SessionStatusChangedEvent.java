package com.roms.dto.event;

import com.roms.enums.ServiceSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusChangedEvent {
    private Long sessionId;
    private Long tableId;
    private String tableName;
    private ServiceSessionStatus status;
    private LocalDateTime occurredAt;
}
