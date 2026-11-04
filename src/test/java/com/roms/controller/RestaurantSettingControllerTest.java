package com.roms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roms.dto.request.UpdateSettingRequest;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.service.RestaurantSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantSettingControllerTest {

    @Mock
    private RestaurantSettingService settingService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantSettingController(settingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSettings_returns200AndPayload() throws Exception {
        RestaurantSettingResponse response = RestaurantSettingResponse.builder()
                .id(1L)
                .menuPriceIncludesVat(true)
                .updatedAt(LocalDateTime.now())
                .build();
        when(settingService.getSettings()).thenReturn(response);

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.menu_price_includes_vat").value(true));
    }

    @Test
    void updateSettings_withValidPayload_returns200() throws Exception {
        UpdateSettingRequest request = UpdateSettingRequest.builder()
                .menuPriceIncludesVat(false)
                .build();
        RestaurantSettingResponse response = RestaurantSettingResponse.builder()
                .id(1L)
                .menuPriceIncludesVat(false)
                .updatedAt(LocalDateTime.now())
                .build();
        when(settingService.updateSettings(any(UpdateSettingRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menu_price_includes_vat").value(false));
    }

    @Test
    void updateSettings_withNullField_returns400() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

