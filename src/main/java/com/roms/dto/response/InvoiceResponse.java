package com.roms.dto.response;

import com.roms.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {
    private Long id;
    private String invoiceCode;
    private Long sessionId;
    private Long tableId;
    private String tableCode;
    private BigDecimal subtotal;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private String discountReason;
    private BigDecimal finalAmount;
    private String note;
    private LocalDateTime createdAt;
    private Long closedById;
    private String closedByName;
    private List<CheckoutItemResponse> items;
}

