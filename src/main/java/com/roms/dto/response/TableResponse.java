package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableResponse {

    private Long id;
    private String tableCode;
    private Integer capacity;
    private Long areaId;
    private String area;
    private Boolean isActive;
    private String realtimeStatus; // "OUT_OF_SERVICE", "OCCUPIED", "EMPTY"
    private Long activeSessionId;
    private String activeSessionCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
