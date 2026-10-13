package com.roms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiConfigTest {

    @Test
    @DisplayName("GeminiConfig creates RestClient bean with configured properties")
    void testGeminiRestClientCreation() {
        GeminiConfig config = new GeminiConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "https://generativelanguage.googleapis.com");
        ReflectionTestUtils.setField(config, "timeoutSeconds", 15);

        RestClient restClient = config.geminiRestClient();
        assertThat(restClient).isNotNull();
    }
}
