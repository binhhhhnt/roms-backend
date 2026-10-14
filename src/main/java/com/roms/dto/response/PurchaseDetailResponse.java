package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseDetailResponse {

    private Long id;
    private LocalDate purchaseDate;
    private BigDecimal totalAmount;
    private Long createdById;
    private String createdByName;
    private String note;
    private List<PurchaseItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
