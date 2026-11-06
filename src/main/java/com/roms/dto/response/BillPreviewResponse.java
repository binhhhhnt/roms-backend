package com.roms.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillPreviewResponse {
    private Long sessionId;
    private String tableCode;
    private LocalDateTime openedAt;
    private List<BillPreviewItemDto> items;
    private BigDecimal subtotal;
    private Boolean menuPriceIncludesVat;
    private List<TaxBreakdownDto> taxBreakdowns;
    private BigDecimal totalTax;
    private BigDecimal amountBeforeDiscount;
    private List<PromotionDto> availablePromotions;
}

