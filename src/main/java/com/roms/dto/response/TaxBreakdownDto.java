package com.roms.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxBreakdownDto {
    private BigDecimal rate;
    private BigDecimal taxableAmount;
    private BigDecimal taxAmount;
}

