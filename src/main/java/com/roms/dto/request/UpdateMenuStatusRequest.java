package com.roms.dto.request;

import com.roms.enums.MenuItemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuStatusRequest {

    @NotNull(message = "Status is required")
    private MenuItemStatus status;
}
