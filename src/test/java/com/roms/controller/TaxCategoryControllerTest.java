package com.roms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roms.dto.request.TaxCategoryRequest;
import com.roms.dto.response.TaxCategoryResponse;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.exception.ResourceNotFoundException;
import com.roms.service.TaxCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaxCategoryControllerTest {

    @Mock
    private TaxCategoryService taxCategoryService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaxCategoryController(taxCategoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllTaxCategories_returns200AndList() throws Exception {
        TaxCategoryResponse r1 = TaxCategoryResponse.builder().id(1L).rate(BigDecimal.valueOf(8.00)).build();
        when(taxCategoryService.getAllTaxCategories()).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/tax-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].rate").value(8.0));
    }

    @Test
    void getTaxCategory_whenExists_returns200() throws Exception {
        TaxCategoryResponse response = TaxCategoryResponse.builder().id(1L).rate(BigDecimal.valueOf(8.00)).build();
        when(taxCategoryService.getTaxCategory(1L)).thenReturn(response);

        mockMvc.perform(get("/api/tax-categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getTaxCategory_whenNotFound_returns404() throws Exception {
        when(taxCategoryService.getTaxCategory(99L))
                .thenThrow(new ResourceNotFoundException("TAX_CATEGORY_NOT_FOUND", "Tax category not found with id: 99"));

        mockMvc.perform(get("/api/tax-categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createTaxCategory_whenValid_returns201() throws Exception {
        TaxCategoryRequest request = TaxCategoryRequest.builder()
                .rate(BigDecimal.valueOf(10.00))
                .build();
        TaxCategoryResponse response = TaxCategoryResponse.builder()
                .id(2L)
                .rate(BigDecimal.valueOf(10.00))
                .createdAt(LocalDateTime.now())
                .build();
        when(taxCategoryService.createTaxCategory(any(TaxCategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/tax-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rate").value(10.0));
    }

    @Test
    void createTaxCategory_whenDuplicate_returns409Conflict() throws Exception {
        TaxCategoryRequest request = TaxCategoryRequest.builder()
                .rate(BigDecimal.valueOf(8.00))
                .build();
        when(taxCategoryService.createTaxCategory(any(TaxCategoryRequest.class)))
                .thenThrow(new BusinessRuleException("TAX_RATE_ALREADY_EXISTS", "Tax rate 8.00% already exists"));

        mockMvc.perform(post("/api/tax-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TAX_RATE_ALREADY_EXISTS"));
    }

    @Test
    void createTaxCategory_whenNegativeRate_returns400() throws Exception {
        TaxCategoryRequest request = TaxCategoryRequest.builder()
                .rate(BigDecimal.valueOf(-5.00))
                .build();

        mockMvc.perform(post("/api/tax-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
