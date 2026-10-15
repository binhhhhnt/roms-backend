package com.roms.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiCandidate {
    private GeminiContent content;
    private String finishReason;
    private Integer index;

    public GeminiCandidate() {
    }

    @Builder
    public GeminiCandidate(GeminiContent content, String finishReason, Integer index) {
        this.content = content;
        this.finishReason = finishReason;
        this.index = index;
    }
}
