package com.roms.service;

import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;
import com.roms.dto.response.MenuItemReadResponse;
import com.roms.common.PageResponse;
import com.roms.enums.MenuItemStatus;
import org.springframework.data.domain.Pageable;

public interface MenuItemService {
    PageResponse<MenuItemReadResponse> getMenuItems(Long categoryId, MenuItemStatus status, int quantity, Pageable pageable);
    MenuItemReadResponse getMenuItem(Long id, int quantity);
    MenuItemResponse createMenuItem(MenuItemWriteRequest request);
    MenuItemResponse updateMenuItem(Long id, MenuItemWriteRequest request);
}
