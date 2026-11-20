package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOrderItemsRequest {

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<ItemReturnDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReturnDto {
        @NotNull(message = "Order item id is required")
        private Long orderItemId;

        @NotNull(message = "Return quantity is required")
        @Min(value = 0, message = "Return quantity must be at least 0")
        private Integer returnQuantity;
    }
}

