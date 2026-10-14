package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.CancelExpenseRequest;
import com.roms.dto.request.CreateExpenseRequest;
import com.roms.dto.request.UpdateExpenseRequest;
import com.roms.dto.response.ExpenseResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface ExpenseService {

    ExpenseResponse createExpense(CreateExpenseRequest request);

    ExpenseResponse updateExpense(Long id, UpdateExpenseRequest request);

    ExpenseResponse cancelExpense(Long id, CancelExpenseRequest request);

    ExpenseResponse getExpenseById(Long id);

    PageResponse<ExpenseResponse> getExpenses(LocalDate startDate, LocalDate endDate, String status, Pageable pageable);
}
