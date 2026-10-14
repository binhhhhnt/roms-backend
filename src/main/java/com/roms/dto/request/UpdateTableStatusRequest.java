package com.roms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UpdateTableStatusRequest {

    public UpdateTableStatusRequest() {
    }

    public UpdateTableStatusRequest(Boolean isActive) {
        this.isActive = isActive;
    }

    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
