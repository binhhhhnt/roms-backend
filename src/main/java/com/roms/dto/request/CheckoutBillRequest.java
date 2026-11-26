package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutBillRequest {

    private String promotionCode;

    @DecimalMin(value = "0.00", message = "Surcharge amount cannot be negative")
    private BigDecimal surchargeAmount;

    @Size(max = 255, message = "Surcharge note must not exceed 255 characters")
    private String surchargeNote;

    @NotEmpty(message = "At least one payment method is required")
    @Valid
    private List<PaymentItemDto> payments;
}
