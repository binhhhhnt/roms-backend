package com.roms.dto.request;

import com.roms.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class CheckoutPreviewRequest {

    public CheckoutPreviewRequest() {
    }

    public CheckoutPreviewRequest(Long sessionId, DiscountType discountType, BigDecimal discountValue) {
        this.sessionId = sessionId;
        this.discountType = discountType != null ? discountType : DiscountType.NONE;
        this.discountValue = discountValue != null ? discountValue : BigDecimal.ZERO;
    }

    @NotNull(message = "Service session ID is required")
    private Long sessionId;

    @Builder.Default
    private DiscountType discountType = DiscountType.NONE;

    @DecimalMin(value = "0.0", message = "Discount value cannot be negative")
    @Builder.Default
    private BigDecimal discountValue = BigDecimal.ZERO;
}

