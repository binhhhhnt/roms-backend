package com.roms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.roms.common.PageResponse;
import com.roms.dto.request.CheckoutPreviewRequest;
import com.roms.dto.request.CheckoutRequest;
import com.roms.dto.response.CheckoutItemResponse;
import com.roms.dto.response.CheckoutPreviewResponse;
import com.roms.dto.response.InvoiceResponse;
import com.roms.enums.DiscountType;
import com.roms.enums.UserRole;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.security.UserPrincipal;
import com.roms.service.InvoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceControllerTest {

    private InvoiceService invoiceService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        invoiceService = mock(InvoiceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InvoiceController(invoiceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .username("user1")
                .role(role)
                .active(true)
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("POST /api/invoices/preview - Should return 200 OK")
    void previewCheckout_returns200() throws Exception {
        authenticate(UserRole.SERVICE_STAFF);

        CheckoutPreviewRequest request = CheckoutPreviewRequest.builder()
                .sessionId(100L)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .build();

        CheckoutPreviewResponse response = CheckoutPreviewResponse.builder()
                .sessionId(100L)
                .tableCode("T01")
                .subtotal(BigDecimal.valueOf(100000))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .discountAmount(BigDecimal.valueOf(10000))
                .finalAmount(BigDecimal.valueOf(90000))
                .items(List.of(
                        CheckoutItemResponse.builder()
                                .menuItemId(1L)
                                .menuItemName("Phở")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(50000))
                                .subtotal(BigDecimal.valueOf(100000))
                                .build()
                ))
                .build();

        when(invoiceService.previewCheckout(any())).thenReturn(response);

        mockMvc.perform(post("/api/invoices/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(100))
                .andExpect(jsonPath("$.data.finalAmount").value(90000));
    }

    @Test
    @DisplayName("POST /api/invoices/checkout - Should return 201 Created")
    void checkout_returns201() throws Exception {
        authenticate(UserRole.SERVICE_STAFF);

        CheckoutRequest request = CheckoutRequest.builder()
                .sessionId(100L)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(20000))
                .discountReason("Khuyến mãi")
                .build();

        InvoiceResponse response = InvoiceResponse.builder()
                .id(1L)
                .invoiceCode("INV-20261103-000100")
                .sessionId(100L)
                .subtotal(BigDecimal.valueOf(100000))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(20000))
                .discountAmount(BigDecimal.valueOf(20000))
                .finalAmount(BigDecimal.valueOf(80000))
                .createdAt(LocalDateTime.now())
                .build();

        when(invoiceService.checkoutAndCloseSession(any())).thenReturn(response);

        mockMvc.perform(post("/api/invoices/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceCode").value("INV-20261103-000100"))
                .andExpect(jsonPath("$.data.finalAmount").value(80000));
    }

    @Test
    @DisplayName("POST /api/invoices/checkout - Should return 400 Bad Request on validation failure")
    void checkout_validationFailure_returns400() throws Exception {
        authenticate(UserRole.SERVICE_STAFF);

        CheckoutRequest request = CheckoutRequest.builder()
                .sessionId(null) // invalid: required
                .build();

        mockMvc.perform(post("/api/invoices/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/invoices/{id} - Should return 200 OK")
    void getInvoiceById_returns200() throws Exception {
        authenticate(UserRole.SERVICE_STAFF);

        InvoiceResponse response = InvoiceResponse.builder()
                .id(1L)
                .invoiceCode("INV-20261103-000100")
                .finalAmount(BigDecimal.valueOf(100000))
                .build();

        when(invoiceService.getInvoiceById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /api/invoices/session/{sessionId} - Should return 200 OK")
    void getInvoiceBySessionId_returns200() throws Exception {
        authenticate(UserRole.TABLE_ACCOUNT);

        InvoiceResponse response = InvoiceResponse.builder()
                .id(1L)
                .sessionId(100L)
                .invoiceCode("INV-20261103-000100")
                .finalAmount(BigDecimal.valueOf(100000))
                .build();

        when(invoiceService.getInvoiceBySessionId(100L)).thenReturn(response);

        mockMvc.perform(get("/api/invoices/session/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(100));
    }

    @Test
    @DisplayName("GET /api/invoices - Should return 200 OK with PageResponse")
    void getInvoices_returns200() throws Exception {
        authenticate(UserRole.OWNER);

        InvoiceResponse response = InvoiceResponse.builder()
                .id(1L)
                .invoiceCode("INV-20261103-000100")
                .finalAmount(BigDecimal.valueOf(100000))
                .build();

        PageResponse<InvoiceResponse> pageResponse = PageResponse.from(
                new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1),
                i -> i
        );

        when(invoiceService.getInvoices(any(), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/invoices")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
