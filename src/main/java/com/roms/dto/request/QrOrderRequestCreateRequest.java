package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrOrderRequestCreateRequest {

    @Size(max = 100, message = "guestLabel must not exceed 100 characters")
    private String guestLabel;

    @NotEmpty(message = "items list must not be empty")
    @Valid
    private List<CreateOrderItemRequest> items;
}
