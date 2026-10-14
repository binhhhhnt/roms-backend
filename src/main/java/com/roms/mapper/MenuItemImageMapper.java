package com.roms.mapper;

import com.roms.dto.response.MenuItemImageResponse;
import com.roms.entity.MenuItemImage;
import org.springframework.stereotype.Component;

@Component
public class MenuItemImageMapper {
    public MenuItemImageResponse toResponse(MenuItemImage image) {
        return MenuItemImageResponse.builder().id(image.getId()).menuItemId(image.getMenuItem().getId())
                .imageUrl(image.getImageUrl()).displayOrder(image.getDisplayOrder()).build();
    }
}
