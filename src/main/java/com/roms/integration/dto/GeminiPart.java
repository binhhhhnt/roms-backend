package com.roms.integration.dto;

import lombok.Builder;
import lombok.Data;

@Data
public class GeminiPart {
    private String text;

    public GeminiPart() {
    }

    @Builder
    public GeminiPart(String text) {
        this.text = text;
    }
}
