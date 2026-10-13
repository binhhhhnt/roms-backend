package com.roms.integration;

import com.roms.exception.GeminiIntegrationException;
import com.roms.integration.dto.GeminiGenerateRequest;
import com.roms.integration.dto.GeminiGenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiClientAdapterImpl implements GeminiClientAdapter {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClientAdapterImpl(
            RestClient geminiRestClient,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-1.5-flash}") String model) {
        this.restClient = geminiRestClient;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = StringUtils.hasText(model) ? model.trim() : "gemini-1.5-flash";
    }

    @Override
    public String generateText(String prompt) {
        GeminiGenerateResponse response = generateContent(GeminiGenerateRequest.of(prompt));
        return response.getFirstText();
    }

    @Override
    public String generateText(String systemInstruction, String prompt) {
        GeminiGenerateResponse response = generateContent(GeminiGenerateRequest.of(systemInstruction, prompt));
        return response.getFirstText();
    }

    @Override
    public GeminiGenerateResponse generateContent(GeminiGenerateRequest request) {
        if (!isConfigured()) {
            log.warn("Gemini API call skipped: GEMINI_API_KEY is not configured in environment or properties");
            throw new GeminiIntegrationException("Gemini API key is not configured. Please set GEMINI_API_KEY in .env");
        }

        String uri = String.format("/v1beta/models/%s:generateContent?key=%s", model, apiKey);

        try {
            log.debug("Calling Gemini API model: {}", model);
            GeminiGenerateResponse response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            if (response == null) {
                throw new GeminiIntegrationException("Received empty response from Gemini API");
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.error("Gemini API error status: {}, body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new GeminiIntegrationException("Gemini API returned error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Gemini API request failed: {}", ex.getMessage(), ex);
            throw new GeminiIntegrationException("Failed to call Gemini API: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }
}
