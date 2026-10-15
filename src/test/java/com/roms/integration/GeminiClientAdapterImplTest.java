package com.roms.integration;

import com.roms.exception.GeminiIntegrationException;
import com.roms.integration.dto.GeminiCandidate;
import com.roms.integration.dto.GeminiContent;
import com.roms.integration.dto.GeminiGenerateRequest;
import com.roms.integration.dto.GeminiGenerateResponse;
import com.roms.integration.dto.GeminiPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiClientAdapterImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @Test
    @DisplayName("isConfigured returns false when API key is empty or null")
    void testIsConfigured_false() {
        GeminiClientAdapterImpl adapterEmpty = new GeminiClientAdapterImpl(restClient, "", "gemini-1.5-flash");
        assertThat(adapterEmpty.isConfigured()).isFalse();

        GeminiClientAdapterImpl adapterNull = new GeminiClientAdapterImpl(restClient, null, "gemini-1.5-flash");
        assertThat(adapterNull.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured returns true when API key is provided")
    void testIsConfigured_true() {
        GeminiClientAdapterImpl adapter = new GeminiClientAdapterImpl(restClient, "AIzaSyTestKey123", "gemini-1.5-flash");
        assertThat(adapter.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("generateContent throws GeminiIntegrationException if API key is not configured")
    void testGenerateContent_notConfigured_throwsException() {
        GeminiClientAdapterImpl adapter = new GeminiClientAdapterImpl(restClient, "", "gemini-1.5-flash");

        assertThatThrownBy(() -> adapter.generateText("Hello"))
                .isInstanceOf(GeminiIntegrationException.class)
                .hasMessageContaining("Gemini API key is not configured");
    }

    @Test
    @DisplayName("generateContent executes RestClient and returns typed response")
    void testGenerateContent_success() {
        String apiKey = "valid-test-key";
        String model = "gemini-1.5-flash";
        GeminiClientAdapterImpl adapter = new GeminiClientAdapterImpl(restClient, apiKey, model);

        GeminiGenerateResponse mockResponse = GeminiGenerateResponse.builder()
                .candidates(Collections.singletonList(
                        GeminiCandidate.builder()
                                .content(GeminiContent.builder()
                                        .role("model")
                                        .parts(Collections.singletonList(GeminiPart.builder().text("Pho Bo recommendation").build()))
                                        .build())
                                .finishReason("STOP")
                                .build()
                ))
                .build();

        when(restClient.post()
                .uri(anyString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(any(GeminiGenerateRequest.class))
                .retrieve()
                .body(GeminiGenerateResponse.class))
                .thenReturn(mockResponse);

        GeminiGenerateResponse result = adapter.generateContent(GeminiGenerateRequest.of("Recommend a soup"));

        assertThat(result).isNotNull();
        assertThat(result.getFirstText()).isEqualTo("Pho Bo recommendation");
    }

    @Test
    @DisplayName("generateText returns first text from candidate")
    void testGenerateText_simplePrompt() {
        String apiKey = "valid-test-key";
        GeminiClientAdapterImpl adapter = new GeminiClientAdapterImpl(restClient, apiKey, "gemini-1.5-flash");

        GeminiGenerateResponse mockResponse = GeminiGenerateResponse.builder()
                .candidates(Collections.singletonList(
                        GeminiCandidate.builder()
                                .content(GeminiContent.builder()
                                        .parts(Collections.singletonList(GeminiPart.builder().text("Bun Cha").build()))
                                        .build())
                                .build()
                ))
                .build();

        when(restClient.post()
                .uri(anyString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(any(GeminiGenerateRequest.class))
                .retrieve()
                .body(GeminiGenerateResponse.class))
                .thenReturn(mockResponse);

        String text = adapter.generateText("System prompt", "User prompt");

        assertThat(text).isEqualTo("Bun Cha");
    }
}
