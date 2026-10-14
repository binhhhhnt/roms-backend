package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.CancelExpenseRequest;
import com.roms.dto.request.CreateExpenseRequest;
import com.roms.dto.request.UpdateExpenseRequest;
import com.roms.dto.response.ExpenseResponse;
import com.roms.entity.Expense;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.ExpenseMapper;
import com.roms.repository.ExpenseRepository;
import com.roms.repository.UserRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.ExpenseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ExpenseMapper expenseMapper = new ExpenseMapper();

    @InjectMocks
    private ExpenseServiceImpl expenseService;

    private User testOwner;
    private Expense activeExpense;
    private Expense cancelledExpense;

    @BeforeEach
    void setUp() {
        testOwner = User.builder()
                .id(1L)
                .username("owner")
                .fullName("Nguyễn Văn Chủ")
                .role(UserRole.OWNER)
                .isActive(true)
                .build();

        activeExpense = Expense.builder()
                .id(10L)
                .name("Tiền điện tháng 10")
                .amount(new BigDecimal("3500000.00"))
                .expenseDate(LocalDate.of(2026, 10, 1))
                .note("Hóa đơn EVN")
                .createdBy(testOwner)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        cancelledExpense = Expense.builder()
                .id(20L)
                .name("Tiền nước tháng 9")
                .amount(new BigDecimal("500000.00"))
                .expenseDate(LocalDate.of(2026, 9, 30))
                .note("Hóa đơn nước")
                .createdBy(testOwner)
                .cancelledAt(LocalDateTime.now().minusDays(1))
                .cancelledBy(testOwner)
                .cancelReason("Nhập trùng hóa đơn")
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .username("owner")
                .role(UserRole.OWNER)
                .sessionId(100L)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createExpense - success")
    void createExpense_success() {
        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .name("Mua đồ dùng bếp")
                .amount(new BigDecimal("450000.00"))
                .expenseDate(LocalDate.of(2026, 10, 10))
                .note("Khăn giấy, xà phòng")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testOwner));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense toSave = invocation.getArgument(0);
            toSave.setId(30L);
            toSave.setCreatedAt(LocalDateTime.now());
            toSave.setUpdatedAt(LocalDateTime.now());
            return toSave;
        });

        ExpenseResponse response = expenseService.createExpense(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getName()).isEqualTo("Mua đồ dùng bếp");
        assertThat(response.getAmount()).isEqualByComparingTo("450000.00");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreatedById()).isEqualTo(1L);
        assertThat(response.getCreatedByName()).isEqualTo("Nguyễn Văn Chủ");
        assertThat(response.getCancelledAt()).isNull();

        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    @DisplayName("createExpense - throws ForbiddenException when unauthenticated")
    void createExpense_unauthenticated() {
        SecurityContextHolder.clearContext();
        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .name("Tiền mạng")
                .amount(new BigDecimal("300000.00"))
                .expenseDate(LocalDate.now())
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Authentication required");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateExpense - success")
    void updateExpense_success() {
        UpdateExpenseRequest request = UpdateExpenseRequest.builder()
                .name("Tiền điện tháng 10 - Điều chỉnh")
                .amount(new BigDecimal("3700000.00"))
                .expenseDate(LocalDate.of(2026, 10, 2))
                .note("Bao gồm VAT")
                .build();

        when(expenseRepository.findById(10L)).thenReturn(Optional.of(activeExpense));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse response = expenseService.updateExpense(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Tiền điện tháng 10 - Điều chỉnh");
        assertThat(response.getAmount()).isEqualByComparingTo("3700000.00");
        assertThat(response.getExpenseDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(response.getNote()).isEqualTo("Bao gồm VAT");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        verify(expenseRepository).save(activeExpense);
    }

    @Test
    @DisplayName("updateExpense - throws BusinessRuleException when expense is already cancelled")
    void updateExpense_alreadyCancelled() {
        UpdateExpenseRequest request = UpdateExpenseRequest.builder()
                .name("Cố tình sửa khoản chi đã hủy")
                .amount(new BigDecimal("100000.00"))
                .expenseDate(LocalDate.now())
                .build();

        when(expenseRepository.findById(20L)).thenReturn(Optional.of(cancelledExpense));

        assertThatThrownBy(() -> expenseService.updateExpense(20L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Expense has already been cancelled and cannot be updated");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateExpense - throws ResourceNotFoundException when id does not exist")
    void updateExpense_notFound() {
        UpdateExpenseRequest request = UpdateExpenseRequest.builder()
                .name("Khoản chi không tồn tại")
                .amount(new BigDecimal("100000.00"))
                .expenseDate(LocalDate.now())
                .build();

        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.updateExpense(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Expense not found with id: 999");
    }

    @Test
    @DisplayName("cancelExpense - success")
    void cancelExpense_success() {
        CancelExpenseRequest request = CancelExpenseRequest.builder()
                .cancelReason("Chi sai mục đích, đã hoàn tiền lại")
                .build();

        when(expenseRepository.findById(10L)).thenReturn(Optional.of(activeExpense));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testOwner));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse response = expenseService.cancelExpense(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getCancelledAt()).isNotNull();
        assertThat(response.getCancelledById()).isEqualTo(1L);
        assertThat(response.getCancelledByName()).isEqualTo("Nguyễn Văn Chủ");
        assertThat(response.getCancelReason()).isEqualTo("Chi sai mục đích, đã hoàn tiền lại");

        verify(expenseRepository).save(activeExpense);
    }

    @Test
    @DisplayName("cancelExpense - throws BusinessRuleException when already cancelled")
    void cancelExpense_alreadyCancelled() {
        CancelExpenseRequest request = CancelExpenseRequest.builder()
                .cancelReason("Hủy lần hai")
                .build();

        when(expenseRepository.findById(20L)).thenReturn(Optional.of(cancelledExpense));

        assertThatThrownBy(() -> expenseService.cancelExpense(20L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Expense has already been cancelled");

        verify(expenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("getExpenseById - success")
    void getExpenseById_success() {
        when(expenseRepository.findById(10L)).thenReturn(Optional.of(activeExpense));

        ExpenseResponse response = expenseService.getExpenseById(10L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Tiền điện tháng 10");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getExpenseById - not found")
    void getExpenseById_notFound() {
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.getExpenseById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Expense not found with id: 999");
    }

    @Test
    @DisplayName("getExpenses - success with pagination and filters")
    void getExpenses_success() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        Pageable pageable = PageRequest.of(0, 10);

        when(expenseRepository.findFiltered(eq(start), eq(end), eq("ACTIVE"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(activeExpense), pageable, 1));

        PageResponse<ExpenseResponse> response = expenseService.getExpenses(start, end, "active", pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Tiền điện tháng 10");
    }

    @Test
    @DisplayName("getExpenses - invalid date range throws BusinessRuleException")
    void getExpenses_invalidDateRange() {
        LocalDate start = LocalDate.of(2026, 10, 31);
        LocalDate end = LocalDate.of(2026, 10, 1);
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> expenseService.getExpenses(start, end, "ALL", pageable))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("startDate cannot be after endDate");
    }

    @Test
    @DisplayName("getExpenses - invalid status filter throws BusinessRuleException")
    void getExpenses_invalidStatus() {
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> expenseService.getExpenses(null, null, "UNKNOWN_STATUS", pageable))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid status filter. Allowed values: ALL, ACTIVE, CANCELLED");
    }
}
