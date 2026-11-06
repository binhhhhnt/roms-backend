package com.roms.dto.response;

import com.roms.enums.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
}

