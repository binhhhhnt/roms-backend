package com.roms.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCategoryResponse {

    private Long id;

    private BigDecimal rate;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}

