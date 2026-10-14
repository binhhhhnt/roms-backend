package com.roms.service;

import com.roms.dto.response.MenuItemImageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface MenuItemImageService {
    MenuItemImageResponse uploadImage(Long menuItemId, MultipartFile file, int displayOrder);
    void deleteImage(Long menuItemId, Long imageId);
}
