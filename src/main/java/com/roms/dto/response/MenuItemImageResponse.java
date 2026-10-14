package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class MenuItemImageResponse {
    private Long id;
    private Long menuItemId;
    private String imageUrl;
    private Integer displayOrder;
}
