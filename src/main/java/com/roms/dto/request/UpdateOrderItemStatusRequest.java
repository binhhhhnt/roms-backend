package com.roms.dto.request;

import com.roms.enums.OrderItemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderItemStatusRequest {

    @NotNull(message = "status is required")
    private OrderItemStatus status;
}
