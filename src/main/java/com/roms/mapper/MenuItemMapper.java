package com.roms.mapper;

import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;
import com.roms.entity.MenuItem;
import org.springframework.stereotype.Component;

@Component
public class MenuItemMapper {
    public void updateEntity(MenuItemWriteRequest request, MenuItem item) {
        item.setName(request.getName().trim());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setIsFeatured(request.getIsFeatured());
    }
    public MenuItemResponse toResponse(MenuItem item) {
        return MenuItemResponse.builder().id(item.getId()).categoryId(item.getCategory().getId())
                .name(item.getName()).description(item.getDescription()).price(item.getPrice())
                .isFeatured(item.getIsFeatured()).status(item.getStatus()).build();
    }
}
