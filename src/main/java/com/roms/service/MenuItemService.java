package com.roms.service;

import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;

public interface MenuItemService {
    MenuItemResponse createMenuItem(MenuItemWriteRequest request);
    MenuItemResponse updateMenuItem(Long id, MenuItemWriteRequest request);
}
