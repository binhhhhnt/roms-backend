package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TableResponse {

    public TableResponse() {
    }

    public TableResponse(Long id, String tableCode, Integer capacity, String area,
                         Boolean isActive, String realtimeStatus, Long activeSessionId,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.area = area;
        this.isActive = isActive;
        this.realtimeStatus = realtimeStatus;
        this.activeSessionId = activeSessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private Long id;
    private String tableCode;
    private Integer capacity;
    private String area;
    private Boolean isActive;
    private String realtimeStatus; // "OUT_OF_SERVICE", "OCCUPIED", "EMPTY"
    private Long activeSessionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
