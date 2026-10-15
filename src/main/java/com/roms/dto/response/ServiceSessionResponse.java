package com.roms.dto.response;

import com.roms.enums.ServiceSessionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ServiceSessionResponse {

    public ServiceSessionResponse() {
    }

    public ServiceSessionResponse(Long id, Long tableId, String tableCode, ServiceSessionStatus status,
                                  LocalDateTime openedAt, Long openedById, String openedByName,
                                  LocalDateTime closedAt, Long closedById, String closedByName) {
        this.id = id;
        this.tableId = tableId;
        this.tableCode = tableCode;
        this.status = status;
        this.openedAt = openedAt;
        this.openedById = openedById;
        this.openedByName = openedByName;
        this.closedAt = closedAt;
        this.closedById = closedById;
        this.closedByName = closedByName;
    }

    private Long id;
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
