package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePurchaseRequest {

    @NotNull(message = "Purchase date must not be null")
    private LocalDate purchaseDate;

    @Size(max = 255, message = "Note must not exceed 255 characters")
    private String note;

    @NotEmpty(message = "Purchase items list must not be empty")
    @Valid
    private List<PurchaseItemRequest> items;
}
