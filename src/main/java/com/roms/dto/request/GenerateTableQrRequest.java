package com.roms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTableQrRequest {

    @NotEmpty(message = "tableIds cannot be empty")
    private List<Long> tableIds;

    private Boolean regenerate;
}
