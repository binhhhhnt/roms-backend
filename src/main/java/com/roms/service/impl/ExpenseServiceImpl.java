package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.request.CancelExpenseRequest;
import com.roms.dto.request.CreateExpenseRequest;
import com.roms.dto.request.UpdateExpenseRequest;
import com.roms.dto.response.ExpenseResponse;
import com.roms.entity.Expense;
import com.roms.entity.User;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.ExpenseMapper;
import com.roms.repository.ExpenseRepository;
import com.roms.repository.UserRepository;
import com.roms.security.SecurityUtils;
import com.roms.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExpenseMapper expenseMapper;

    @Override
    @Transactional
    public ExpenseResponse createExpense(CreateExpenseRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        Expense expense = expenseMapper.toEntity(request, currentUser);
        Expense savedExpense = expenseRepository.save(expense);

        log.info("Expense created with id: {} by user: {}", savedExpense.getId(), currentUser.getUsername());
        return expenseMapper.toResponse(savedExpense);
    }

    @Override
    @Transactional
    public ExpenseResponse updateExpense(Long id, UpdateExpenseRequest request) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

        if (expense.getCancelledAt() != null) {
            throw new BusinessRuleException("Expense has already been cancelled and cannot be updated");
        }

        expenseMapper.updateEntity(expense, request);
        Expense updatedExpense = expenseRepository.save(expense);

        log.info("Expense updated with id: {}", updatedExpense.getId());
        return expenseMapper.toResponse(updatedExpense);
    }

    @Override
    @Transactional
    public ExpenseResponse cancelExpense(Long id, CancelExpenseRequest request) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

        if (expense.getCancelledAt() != null) {
            throw new BusinessRuleException("Expense has already been cancelled");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        expense.setCancelledAt(LocalDateTime.now());
        expense.setCancelledBy(currentUser);
        expense.setCancelReason(request.getCancelReason());

        Expense cancelledExpense = expenseRepository.save(expense);
        log.info("Expense cancelled with id: {} by user: {}", cancelledExpense.getId(), currentUser.getUsername());

        return expenseMapper.toResponse(cancelledExpense);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        return expenseMapper.toResponse(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> getExpenses(
            LocalDate startDate,
            LocalDate endDate,
            String status,
            Pageable pageable) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessRuleException("startDate cannot be after endDate");
        }

        String normalizedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            normalizedStatus = status.trim().toUpperCase();
            if (!normalizedStatus.equals("ALL") && !normalizedStatus.equals("ACTIVE") && !normalizedStatus.equals("CANCELLED")) {
                throw new BusinessRuleException("Invalid status filter. Allowed values: ALL, ACTIVE, CANCELLED");
            }
        }

        Page<Expense> page = expenseRepository.findFiltered(startDate, endDate, normalizedStatus, pageable);
        return PageResponse.from(page, expenseMapper::toResponse);
    }
}
