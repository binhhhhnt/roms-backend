package com.roms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roms.common.PageResponse;
import com.roms.config.SecurityConfig;
import com.roms.dto.request.CancelExpenseRequest;
import com.roms.dto.request.CreateExpenseRequest;
import com.roms.dto.request.UpdateExpenseRequest;
import com.roms.dto.response.ExpenseResponse;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.exception.ResourceNotFoundException;
import com.roms.security.JwtAuthenticationFilter;
import com.roms.service.ExpenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExpenseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/expenses - Owner creates expense successfully (201 Created)")
    void createExpense_asOwner_success() throws Exception {
        String requestJson = """
                {
                    "name": "Tiền mạng Internet FPT",
                    "amount": 350000.00,
                    "expenseDate": "2026-10-15",
                    "note": "Gói doanh nghiệp"
                }
                """;

        ExpenseResponse mockResponse = ExpenseResponse.builder()
                .id(1L)
                .name("Tiền mạng Internet FPT")
                .amount(new BigDecimal("350000.00"))
                .expenseDate(LocalDate.of(2026, 10, 15))
                .note("Gói doanh nghiệp")
                .status("ACTIVE")
                .createdById(1L)
                .createdByName("Nguyễn Văn Chủ")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(expenseService.createExpense(any(CreateExpenseRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/expenses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("Tiền mạng Internet FPT"))
                .andExpect(jsonPath("$.data.amount").value(350000.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/expenses - Validation error when amount is negative (400 Bad Request)")
    void createExpense_negativeAmount_validationFailure() throws Exception {
        String invalidJson = """
                {
                    "name": "Khoản chi sai",
                    "amount": -50000.00,
                    "expenseDate": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/expenses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "SERVICE_STAFF")
    @DisplayName("POST /api/expenses - Non-owner role is forbidden (403 Forbidden)")
    void createExpense_asServiceStaff_forbidden() throws Exception {
        String requestJson = """
                {
                    "name": "Nhân viên cố tạo chi phí",
                    "amount": 100000.00,
                    "expenseDate": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/expenses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/expenses - Unauthenticated request is rejected (401 Unauthorized)")
    void createExpense_unauthenticated() throws Exception {
        String requestJson = """
                {
                    "name": "Chưa đăng nhập",
                    "amount": 100000.00,
                    "expenseDate": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/expenses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/expenses - List expenses with pagination (200 OK)")
    void getExpenses_asOwner_success() throws Exception {
        ExpenseResponse item = ExpenseResponse.builder()
                .id(1L)
                .name("Tiền điện")
                .amount(new BigDecimal("3500000.00"))
                .status("ACTIVE")
                .build();

        PageResponse<ExpenseResponse> pageResponse = PageResponse.<ExpenseResponse>builder()
                .content(List.of(item))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(expenseService.getExpenses(any(), any(), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/expenses")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Tiền điện"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/expenses/{id} - Found (200 OK)")
    void getExpenseById_success() throws Exception {
        ExpenseResponse item = ExpenseResponse.builder()
                .id(1L)
                .name("Tiền nước")
                .amount(new BigDecimal("500000.00"))
                .status("ACTIVE")
                .build();

        when(expenseService.getExpenseById(1L)).thenReturn(item);

        mockMvc.perform(get("/api/expenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("Tiền nước"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/expenses/{id} - Not Found (404 Not Found)")
    void getExpenseById_notFound() throws Exception {
        when(expenseService.getExpenseById(999L))
                .thenThrow(new ResourceNotFoundException("Expense not found with id: 999"));

        mockMvc.perform(get("/api/expenses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PUT /api/expenses/{id} - Update success (200 OK)")
    void updateExpense_success() throws Exception {
        String requestJson = """
                {
                    "name": "Tiền điện tháng 10 - Điều chỉnh",
                    "amount": 3700000.00,
                    "expenseDate": "2026-10-02"
                }
                """;

        ExpenseResponse updatedResponse = ExpenseResponse.builder()
                .id(1L)
                .name("Tiền điện tháng 10 - Điều chỉnh")
                .amount(new BigDecimal("3700000.00"))
                .status("ACTIVE")
                .build();

        when(expenseService.updateExpense(eq(1L), any(UpdateExpenseRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/expenses/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Tiền điện tháng 10 - Điều chỉnh"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PUT /api/expenses/{id} - Conflict when expense already cancelled (409 Conflict)")
    void updateExpense_alreadyCancelled_conflict() throws Exception {
        String requestJson = """
                {
                    "name": "Khoản chi đã hủy",
                    "amount": 100000.00,
                    "expenseDate": "2026-10-02"
                }
                """;

        when(expenseService.updateExpense(eq(1L), any(UpdateExpenseRequest.class)))
                .thenThrow(new BusinessRuleException("Expense has already been cancelled and cannot be updated"));

        mockMvc.perform(put("/api/expenses/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Expense has already been cancelled and cannot be updated"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/expenses/{id}/cancel - Success (200 OK)")
    void cancelExpense_success() throws Exception {
        String requestJson = """
                {
                    "cancelReason": "Nhập sai hóa đơn"
                }
                """;

        ExpenseResponse cancelledResponse = ExpenseResponse.builder()
                .id(1L)
                .name("Tiền điện")
                .status("CANCELLED")
                .cancelReason("Nhập sai hóa đơn")
                .cancelledAt(LocalDateTime.now())
                .build();

        when(expenseService.cancelExpense(eq(1L), any(CancelExpenseRequest.class))).thenReturn(cancelledResponse);

        mockMvc.perform(post("/api/expenses/1/cancel")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Nhập sai hóa đơn"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/expenses/{id}/cancel - Conflict when already cancelled (409 Conflict)")
    void cancelExpense_alreadyCancelled_conflict() throws Exception {
        String requestJson = """
                {
                    "cancelReason": "Hủy lần nữa"
                }
                """;

        when(expenseService.cancelExpense(eq(1L), any(CancelExpenseRequest.class)))
                .thenThrow(new BusinessRuleException("Expense has already been cancelled"));

        mockMvc.perform(post("/api/expenses/1/cancel")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Expense has already been cancelled"));
    }
}
