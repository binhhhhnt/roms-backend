package com.roms.controller;

import tools.jackson.databind.ObjectMapper;
import com.roms.dto.request.AiRecommendationRequest;
import com.roms.dto.response.AiRecommendationItem;
import com.roms.dto.response.AiRecommendationResponse;
import com.roms.service.AiAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiAssistantControllerTest {

    @Mock
    private AiAssistantService aiAssistantService;

    @InjectMocks
    private AiAssistantController aiAssistantController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiAssistantController).build();
    }

    @Test
    @DisplayName("POST /api/ai/recommendations - should return 200 with wrapped ApiResponse")
    void shouldReturnRecommendationsSuccessfully() throws Exception {
        AiRecommendationResponse mockResponse = AiRecommendationResponse.builder()
                .message("Dưới đây là các món gợi ý:")
                .recommendations(List.of(
                        AiRecommendationItem.builder()
                                .menuItemId(1L)
                                .name("Phở bò đặc biệt")
                                .price(new BigDecimal("65000"))
                                .categoryName("Món chính")
                                .reason("Nước dùng thanh đạm")
                                .isSellable(true)
                                .build()
                ))
                .build();

        when(aiAssistantService.getRecommendations(any(AiRecommendationRequest.class)))
                .thenReturn(mockResponse);

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .preferences("thích ăn thanh đạm")
                .partySize(2)
                .build();

        mockMvc.perform(post("/api/ai/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("AI recommendations generated successfully"))
                .andExpect(jsonPath("$.data.recommendations[0].name").value("Phở bò đặc biệt"))
                .andExpect(jsonPath("$.data.recommendations[0].price").value(65000));
    }
}
