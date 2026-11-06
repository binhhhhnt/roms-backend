package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutBillRequest {

    private String promotionCode;

    @NotEmpty(message = "At least one payment method is required")
    @Valid
    private List<PaymentItemDto> payments;
}

