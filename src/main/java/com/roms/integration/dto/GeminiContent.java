package com.roms.integration.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class GeminiContent {
    private String role;
    private List<GeminiPart> parts;

    public GeminiContent() {
    }

    @Builder
    public GeminiContent(String role, List<GeminiPart> parts) {
        this.role = role;
        this.parts = parts;
    }

    public static GeminiContent user(String text) {
        return GeminiContent.builder()
                .role("user")
                .parts(Collections.singletonList(GeminiPart.builder().text(text).build()))
                .build();
    }

    public static GeminiContent system(String text) {
        return GeminiContent.builder()
                .role("system")
                .parts(Collections.singletonList(GeminiPart.builder().text(text).build()))
                .build();
    }
}
