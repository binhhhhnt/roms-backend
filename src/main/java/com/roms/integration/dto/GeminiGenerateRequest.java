package com.roms.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiGenerateRequest {
    private List<GeminiContent> contents;
    private GeminiContent systemInstruction;
    private GeminiGenerationConfig generationConfig;

    public GeminiGenerateRequest() {
    }

    @Builder
    public GeminiGenerateRequest(List<GeminiContent> contents, GeminiContent systemInstruction, GeminiGenerationConfig generationConfig) {
        this.contents = contents;
        this.systemInstruction = systemInstruction;
        this.generationConfig = generationConfig;
    }

    public static GeminiGenerateRequest of(String prompt) {
        return GeminiGenerateRequest.builder()
                .contents(Collections.singletonList(GeminiContent.user(prompt)))
                .build();
    }

    public static GeminiGenerateRequest of(String systemPrompt, String userPrompt) {
        return GeminiGenerateRequest.builder()
                .systemInstruction(GeminiContent.system(systemPrompt))
                .contents(Collections.singletonList(GeminiContent.user(userPrompt)))
                .build();
    }
}
