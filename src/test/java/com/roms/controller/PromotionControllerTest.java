package com.roms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roms.dto.request.PromotionWriteRequest;
import com.roms.dto.response.PromotionDto;
import com.roms.dto.response.PromotionResponse;
import com.roms.enums.DiscountType;
import com.roms.enums.UserRole;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.security.UserPrincipal;
import com.roms.service.PromotionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PromotionControllerTest {

    private PromotionService promotionService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        promotionService = mock(PromotionService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mvc = MockMvcBuilders.standaloneSetup(new PromotionController(promotionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserRole role) {
        var principal = UserPrincipal.builder().id(1L).username("testuser").role(role).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void getPromotions_ReturnsPage() throws Exception {
        authenticate(UserRole.OWNER);
        PromotionResponse p = PromotionResponse.builder()
                .id(1L)
                .code("PROMO10")
                .name("Test Promo")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .statusBadge("ACTIVE")
                .build();

        when(promotionService.getPromotions(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].code").value("PROMO10"));
    }

    @Test
    void getValidPromotions_ReturnsList() throws Exception {
        authenticate(UserRole.SERVICE_STAFF);
        PromotionDto dto = PromotionDto.builder()
                .id(1L)
                .code("VALID10")
                .name("Valid Promo")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .build();

        when(promotionService.getValidPromotions()).thenReturn(List.of(dto));

        mvc.perform(get("/api/promotions/valid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("VALID10"));
    }

    @Test
    void createPromotion_Success() throws Exception {
        authenticate(UserRole.OWNER);
        PromotionWriteRequest request = PromotionWriteRequest.builder()
                .name("Khuyến mãi mới")
                .code("NEWCODE")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(50000))
                .build();

        PromotionResponse response = PromotionResponse.builder()
                .id(10L)
                .name("Khuyến mãi mới")
                .code("NEWCODE")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(50000))
                .build();

        when(promotionService.createPromotion(any(PromotionWriteRequest.class))).thenReturn(response);

        mvc.perform(post("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("NEWCODE"));
    }

    @Test
    void createPromotion_ValidationFailure_BlankName() throws Exception {
        authenticate(UserRole.OWNER);
        PromotionWriteRequest request = PromotionWriteRequest.builder()
                .name("") // blank!
                .code("VALIDCODE")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .build();

        mvc.perform(post("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(promotionService, never()).createPromotion(any());
    }

    @Test
    void deletePromotion_Success() throws Exception {
        authenticate(UserRole.OWNER);
        doNothing().when(promotionService).deletePromotion(5L);

        mvc.perform(delete("/api/promotions/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(promotionService).deletePromotion(5L);
    }

    @Test
    void toggleActive_Success() throws Exception {
        authenticate(UserRole.OWNER);
        PromotionResponse response = PromotionResponse.builder()
                .id(5L)
                .code("TOGGLE")
                .isActive(false)
                .statusBadge("INACTIVE")
                .build();

        when(promotionService.toggleActive(5L)).thenReturn(response);

        mvc.perform(patch("/api/promotions/5/toggle-status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }
}

