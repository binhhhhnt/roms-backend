package com.roms.integration;

import com.roms.integration.dto.GeminiGenerateRequest;
import com.roms.integration.dto.GeminiGenerateResponse;

/**
 * Foundation client adapter for communicating with Google Gemini API.
 * Provides raw AI generation capabilities consumed by QUANG (BE-022, BE-024).
 * Business recommendation logic, live recipe validation, and menu sellability belong to the service layer.
 */
public interface GeminiClientAdapter {

    /**
     * Generate content text from a simple prompt.
     *
     * @param prompt input prompt
     * @return generated response text
     */
    String generateText(String prompt);

    /**
     * Generate content text with a system instruction and user prompt.
     *
     * @param systemInstruction system-level directive
     * @param prompt user prompt
     * @return generated response text
     */
    String generateText(String systemInstruction, String prompt);

    /**
     * Execute full typed Gemini content generation request.
     *
     * @param request typed request
     * @return typed response
     */
    GeminiGenerateResponse generateContent(GeminiGenerateRequest request);

    /**
     * Check if Gemini API key is configured.
     *
     * @return true if an API key is present
     */
    boolean isConfigured();
}
