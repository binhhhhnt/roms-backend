package com.roms.mapper;

import com.roms.dto.response.InventoryCheckItemResponse;
import com.roms.dto.response.InventoryCheckResponse;
import com.roms.entity.InventoryCheck;
import com.roms.entity.InventoryCheckItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class InventoryCheckMapper {

    public InventoryCheckItemResponse toItemResponse(InventoryCheckItem item) {
        if (item == null) {
            return null;
        }

        return InventoryCheckItemResponse.builder()
                .id(item.getId())
                .ingredientId(item.getIngredient() != null ? item.getIngredient().getId() : null)
                .ingredientName(item.getIngredient() != null ? item.getIngredient().getName() : null)
                .unit(item.getIngredient() != null ? item.getIngredient().getUnit() : null)
                .systemQuantity(item.getSystemQuantity())
                .actualQuantity(item.getActualQuantity())
                .difference(item.getDifference())
                .reason(item.getReason())
                .createdAt(item.getCreatedAt())
                .build();
    }

    public InventoryCheckResponse toResponse(InventoryCheck check, List<InventoryCheckItem> items) {
        if (check == null) {
            return null;
        }

        List<InventoryCheckItemResponse> itemResponses = items != null
                ? items.stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        return InventoryCheckResponse.builder()
                .id(check.getId())
                .checkedById(check.getCheckedBy() != null ? check.getCheckedBy().getId() : null)
                .checkedByName(check.getCheckedBy() != null ? check.getCheckedBy().getFullName() : null)
                .checkedAt(check.getCheckedAt())
                .note(check.getNote())
                .items(itemResponses)
                .createdAt(check.getCreatedAt())
                .updatedAt(check.getUpdatedAt())
                .build();
    }
}
