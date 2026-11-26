package com.roms.dto.response;

import com.roms.enums.ServiceSessionStatus;
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
public class ServiceSessionResponse {

    private Long id;
    private String sessionCode;
    private Long tableId;
    private String tableCode;
    private ServiceSessionStatus status;
    private LocalDateTime openedAt;
    private Long openedById;
    private String openedByName;
    private LocalDateTime closedAt;
    private Long closedById;
    private String closedByName;
}
