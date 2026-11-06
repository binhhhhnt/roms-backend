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
public class BillResponse {
    private Long id;
    private String billCode;
    private Long sessionId;
    private String tableCode;
    private BigDecimal amountBeforeDiscount;
    private Long promotionId;
    private String promotionCode;
    private String promotionName;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private List<BillItemResponse> items;
    private List<PaymentResponse> payments;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
}
