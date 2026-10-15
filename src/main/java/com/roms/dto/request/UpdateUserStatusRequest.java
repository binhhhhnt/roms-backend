package com.roms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UpdateUserStatusRequest {

    public UpdateUserStatusRequest() {
    }

    public UpdateUserStatusRequest(Boolean isActive) {
        this.isActive = isActive;
    }

    @NotNull(message = "isActive cannot be null")
    private Boolean isActive;
}
