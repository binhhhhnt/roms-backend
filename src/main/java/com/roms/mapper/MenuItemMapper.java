package com.roms.mapper;

import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;
import com.roms.dto.response.MenuItemReadResponse;
import com.roms.dto.response.CategoryResponse;
import com.roms.dto.response.MenuItemImageResponse;
import com.roms.dto.response.MenuItemSellabilityResponse;
import java.math.BigDecimal;
import java.util.List;
import com.roms.entity.MenuItem;
import org.springframework.stereotype.Component;

@Component
public class MenuItemMapper {
    public MenuItemReadResponse toReadResponse(MenuItem item, CategoryResponse category,
            List<MenuItemImageResponse> images, MenuItemSellabilityResponse sellability) {
        Long taxCatId = item.getTaxCategory() != null ? item.getTaxCategory().getId() : null;
        BigDecimal taxRate = item.getTaxCategory() != null ? item.getTaxCategory().getRate() : null;

        return MenuItemReadResponse.builder().id(item.getId()).category(category)
                .name(item.getName()).description(item.getDescription()).price(item.getPrice())
                .isFeatured(item.getIsFeatured()).isDirectSale(item.getIsDirectSale()).status(item.getStatus()).images(images)
                .taxCategoryId(taxCatId)
                .taxRate(taxRate)
                .availability(MenuItemReadResponse.Availability.builder()
                        .sellable(sellability.isSellable()).reason(sellability.getReason())
                        .requestedQuantity(sellability.getRequestedQuantity())
                        .maxPossibleQuantity(sellability.getMaxPossibleQuantity()).build())
                .build();
    }

    public void updateEntity(MenuItemWriteRequest request, MenuItem item) {
        item.setName(request.getName().trim());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setIsFeatured(request.getIsFeatured());
        if (request.getIsDirectSale() != null) {
            item.setIsDirectSale(request.getIsDirectSale());
        }
    }

    public MenuItemResponse toResponse(MenuItem item) {
        Long taxCatId = item.getTaxCategory() != null ? item.getTaxCategory().getId() : null;
        BigDecimal taxRate = item.getTaxCategory() != null ? item.getTaxCategory().getRate() : null;

        return MenuItemResponse.builder().id(item.getId()).categoryId(item.getCategory().getId())
                .name(item.getName()).description(item.getDescription()).price(item.getPrice())
                .isFeatured(item.getIsFeatured()).isDirectSale(item.getIsDirectSale()).status(item.getStatus())
                .taxCategoryId(taxCatId).taxRate(taxRate).build();
    }
}
