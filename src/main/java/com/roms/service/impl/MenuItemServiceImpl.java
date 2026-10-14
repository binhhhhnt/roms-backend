package com.roms.service.impl;

import com.roms.dto.request.MenuItemWriteRequest;
import com.roms.dto.response.MenuItemResponse;
import com.roms.entity.MenuItem;
import com.roms.enums.MenuItemStatus;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.ValidationException;
import com.roms.mapper.MenuItemMapper;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {
    private final MenuItemRepository repository;
    private final CategoryRepository categories;
    private final MenuItemMapper mapper;
    private final MenuContextInvalidator invalidator;

    @Override @Transactional
    public MenuItemResponse createMenuItem(MenuItemWriteRequest request) {
        CatalogAccess.requireOwner();
        MenuItem item = new MenuItem();
        item.setStatus(MenuItemStatus.AVAILABLE);
        return write(request, item);
    }

    @Override @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemWriteRequest request) {
        CatalogAccess.requireOwner();
        if (id == null || id <= 0) throw new ValidationException("Menu item id must be positive");
        MenuItem item = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        return write(request, item);
    }

    private MenuItemResponse write(MenuItemWriteRequest request, MenuItem item) {
        item.setCategory(categories.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found")));
        mapper.updateEntity(request, item);
        MenuItem saved = repository.save(item);
        invalidator.afterCommit();
        return mapper.toResponse(saved);
    }
}
