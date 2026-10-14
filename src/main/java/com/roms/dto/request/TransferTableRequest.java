package com.roms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TransferTableRequest {

    public TransferTableRequest() {
    }

    public TransferTableRequest(Long toTableId) {
        this.toTableId = toTableId;
    }

    @NotNull(message = "Target table ID is required")
    private Long toTableId;
}
