package com.roms.mapper;

import com.roms.dto.response.PurchaseDetailResponse;
import com.roms.dto.response.PurchaseItemResponse;
import com.roms.dto.response.PurchaseResponse;
import com.roms.entity.Purchase;
import com.roms.entity.PurchaseItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class PurchaseMapper {

    public PurchaseItemResponse toItemResponse(PurchaseItem item) {
        if (item == null) {
            return null;
        }

        return PurchaseItemResponse.builder()
                .id(item.getId())
                .ingredientId(item.getIngredient() != null ? item.getIngredient().getId() : null)
                .ingredientName(item.getIngredient() != null ? item.getIngredient().getName() : null)
                .unit(item.getIngredient() != null ? item.getIngredient().getUnit() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public PurchaseResponse toResponse(Purchase purchase) {
        if (purchase == null) {
            return null;
        }

        return PurchaseResponse.builder()
                .id(purchase.getId())
                .purchaseDate(purchase.getPurchaseDate())
                .totalAmount(purchase.getTotalAmount())
                .createdById(purchase.getCreatedBy() != null ? purchase.getCreatedBy().getId() : null)
                .createdByName(purchase.getCreatedBy() != null ? purchase.getCreatedBy().getFullName() : null)
                .note(purchase.getNote())
                .createdAt(purchase.getCreatedAt())
                .updatedAt(purchase.getUpdatedAt())
                .build();
    }

    public PurchaseDetailResponse toDetailResponse(Purchase purchase, List<PurchaseItem> items) {
        if (purchase == null) {
            return null;
        }

        List<PurchaseItemResponse> itemResponses = items != null
                ? items.stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        return PurchaseDetailResponse.builder()
                .id(purchase.getId())
                .purchaseDate(purchase.getPurchaseDate())
                .totalAmount(purchase.getTotalAmount())
                .createdById(purchase.getCreatedBy() != null ? purchase.getCreatedBy().getId() : null)
                .createdByName(purchase.getCreatedBy() != null ? purchase.getCreatedBy().getFullName() : null)
                .note(purchase.getNote())
                .items(itemResponses)
                .createdAt(purchase.getCreatedAt())
                .updatedAt(purchase.getUpdatedAt())
                .build();
    }
}
