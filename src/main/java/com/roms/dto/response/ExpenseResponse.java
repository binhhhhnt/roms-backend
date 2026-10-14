package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private String name;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String note;
    private String status;
    private Long createdById;
    private String createdByName;
    private LocalDateTime cancelledAt;
    private Long cancelledById;
    private String cancelledByName;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
