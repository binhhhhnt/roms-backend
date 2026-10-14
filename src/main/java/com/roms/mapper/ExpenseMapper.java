package com.roms.mapper;

import com.roms.dto.request.CreateExpenseRequest;
import com.roms.dto.request.UpdateExpenseRequest;
import com.roms.dto.response.ExpenseResponse;
import com.roms.entity.Expense;
import com.roms.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    public ExpenseResponse toResponse(Expense expense) {
        if (expense == null) {
            return null;
        }

        String status = expense.getCancelledAt() != null ? "CANCELLED" : "ACTIVE";
        Long createdById = expense.getCreatedBy() != null ? expense.getCreatedBy().getId() : null;
        String createdByName = expense.getCreatedBy() != null ? expense.getCreatedBy().getFullName() : null;
        Long cancelledById = expense.getCancelledBy() != null ? expense.getCancelledBy().getId() : null;
        String cancelledByName = expense.getCancelledBy() != null ? expense.getCancelledBy().getFullName() : null;

        return ExpenseResponse.builder()
                .id(expense.getId())
                .name(expense.getName())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate())
                .note(expense.getNote())
                .status(status)
                .createdById(createdById)
                .createdByName(createdByName)
                .cancelledAt(expense.getCancelledAt())
                .cancelledById(cancelledById)
                .cancelledByName(cancelledByName)
                .cancelReason(expense.getCancelReason())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    public Expense toEntity(CreateExpenseRequest request, User createdBy) {
        if (request == null) {
            return null;
        }

        return Expense.builder()
                .name(request.getName() != null ? request.getName().trim() : null)
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate())
                .note(request.getNote())
                .createdBy(createdBy)
                .build();
    }

    public void updateEntity(Expense expense, UpdateExpenseRequest request) {
        if (expense == null || request == null) {
            return;
        }

        expense.setName(request.getName() != null ? request.getName().trim() : expense.getName());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setNote(request.getNote());
    }
}
