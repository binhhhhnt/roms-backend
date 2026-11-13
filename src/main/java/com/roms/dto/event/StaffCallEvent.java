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
public class StaffCallEvent {
    private Long tableId;
    private String tableCode;
    private LocalDateTime requestedAt;
    private String requestId;
}

