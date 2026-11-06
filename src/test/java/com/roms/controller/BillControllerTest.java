package com.roms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roms.dto.request.CheckoutBillRequest;
import com.roms.dto.request.PaymentItemDto;
import com.roms.dto.response.BillPreviewResponse;
import com.roms.dto.response.BillResponse;
import com.roms.enums.PaymentMethod;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.service.BillService;
import com.roms.service.PrinterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BillControllerTest {

    @Mock
    private BillService billService;

    @Mock
    private PrinterService printerService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BillController(billService, printerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getBillPreview_returns200() throws Exception {
        BillPreviewResponse preview = BillPreviewResponse.builder()
                .sessionId(10L)
                .tableCode("Bàn 01")
                .subtotal(BigDecimal.valueOf(100000))
                .amountBeforeDiscount(BigDecimal.valueOf(100000))
                .items(List.of())
                .build();
        when(billService.getBillPreview(10L)).thenReturn(preview);

        mockMvc.perform(get("/api/bills/sessions/10/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tableCode").value("Bàn 01"));
    }

    @Test
    void checkout_returns201() throws Exception {
        CheckoutBillRequest request = CheckoutBillRequest.builder()
                .payments(List.of(PaymentItemDto.builder()
                        .paymentMethod(PaymentMethod.CASH)
                        .amount(BigDecimal.valueOf(100000))
                        .build()))
                .build();

        BillResponse response = BillResponse.builder()
                .id(1L)
                .billCode("BILL-20261106-0001")
                .totalAmount(BigDecimal.valueOf(100000))
                .build();
        when(billService.checkout(eq(10L), any(CheckoutBillRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/bills/sessions/10/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.billCode").value("BILL-20261106-0001"));
    }

    @Test
    void printBill_callsPrinterService_returns200() throws Exception {
        mockMvc.perform(post("/api/bills/1/print"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Yêu cầu in hóa đơn đã được gửi tới máy in"));

        verify(printerService).printBill(1L);
    }

    @Test
    void printProforma_callsPrinterService_returns200() throws Exception {
        mockMvc.perform(post("/api/bills/sessions/10/print-proforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Yêu cầu in phiếu tạm tính đã được gửi tới máy in"));

        verify(printerService).printProforma(10L);
    }
}

