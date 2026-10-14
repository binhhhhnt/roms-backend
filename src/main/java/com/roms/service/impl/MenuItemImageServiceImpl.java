package com.roms.service.impl;

import com.roms.dto.response.MenuItemImageResponse;
import com.roms.entity.MenuItem;
import com.roms.entity.MenuItemImage;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.ValidationException;
import com.roms.integration.CloudinaryClientAdapter;
import com.roms.mapper.MenuItemImageMapper;
import com.roms.repository.MenuItemImageRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.service.MenuItemImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Arrays;

@Service @RequiredArgsConstructor @Slf4j
public class MenuItemImageServiceImpl implements MenuItemImageService {
    private final MenuItemRepository menus;
    private final MenuItemImageRepository images;
    private final CloudinaryClientAdapter client;
    private final MenuItemImageMapper mapper;
    private final MenuContextInvalidator invalidator;

    @Override @Transactional
    public MenuItemImageResponse uploadImage(Long menuItemId, MultipartFile file, int displayOrder) {
        CatalogAccess.requireOwner();
        byte[] bytes = validateFile(file);
        MenuItem menu = lockMenu(menuItemId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Image upload requires an active transaction");
        }
        // Placeholder is flushed only inside this transaction to obtain the BIGSERIAL id.
        MenuItemImage image = images.saveAndFlush(MenuItemImage.builder().menuItem(menu)
                .imageUrl("").displayOrder(displayOrder).build());
        String publicId = publicId(menuItemId, image.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { client.delete(publicId); }
                    catch (Exception ex) { log.warn("Image cleanup failed for asset {}", publicId); }
                }
            }
        });
        image.setImageUrl(client.upload(bytes, publicId));
        images.saveAndFlush(image);
        invalidator.afterCommit();
        return mapper.toResponse(image);
    }

    @Override @Transactional
    public void deleteImage(Long menuItemId, Long imageId) {
        CatalogAccess.requireOwner();
        lockMenu(menuItemId);
        if (imageId == null || imageId <= 0) throw new ValidationException("Image id must be positive");
        MenuItemImage image = images.findByIdAndMenuItemId(imageId, menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item image not found"));
        client.delete(publicId(menuItemId, imageId));
        images.delete(image);
        images.flush();
        invalidator.afterCommit();
    }

    private MenuItem lockMenu(Long id) {
        if (id == null || id <= 0) throw new ValidationException("Menu item id must be positive");
        return menus.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
    }
    private String publicId(Long menuId, Long imageId) {
        return "roms/menu-items/" + menuId + "/images/" + imageId;
    }
    private byte[] validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new ValidationException("Image must be nonempty and at most 5 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            boolean jpg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean webp = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            String type = file.getContentType();
            if (!(jpg && "image/jpeg".equals(type) || png && "image/png".equals(type) || webp && "image/webp".equals(type))) {
                throw new ValidationException("Only JPEG, PNG and WebP images with matching content type are allowed");
            }
            return bytes;
        } catch (IOException ex) { throw new ValidationException("Unable to read image"); }
    }
}
