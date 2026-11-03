package com.roms.dto.request;

import com.roms.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class CheckoutRequest {

    public CheckoutRequest() {
    }

    public CheckoutRequest(Long sessionId, DiscountType discountType, BigDecimal discountValue,
                           String discountReason, String note) {
        this.sessionId = sessionId;
        this.discountType = discountType != null ? discountType : DiscountType.NONE;
        this.discountValue = discountValue != null ? discountValue : BigDecimal.ZERO;
        this.discountReason = discountReason;
        this.note = note;
    }

    @NotNull(message = "Service session ID is required")
    private Long sessionId;

    @Builder.Default
    private DiscountType discountType = DiscountType.NONE;

    @DecimalMin(value = "0.0", message = "Discount value cannot be negative")
    @Builder.Default
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Size(max = 255, message = "Discount reason cannot exceed 255 characters")
    private String discountReason;

    @Size(max = 255, message = "Note cannot exceed 255 characters")
    private String note;
}

