package com.roms.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiGenerationConfig {
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxOutputTokens;

    public GeminiGenerationConfig() {
    }

    @Builder
    public GeminiGenerationConfig(Double temperature, Double topP, Integer topK, Integer maxOutputTokens) {
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxOutputTokens = maxOutputTokens;
    }
}
