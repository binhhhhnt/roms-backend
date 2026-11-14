package com.roms.service.impl;

import com.roms.entity.Category;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.ValidationException;
import com.roms.repository.CategoryRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.MenuItemImageRepository;
import com.roms.repository.OrderItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.CatalogDeletionService;
import com.roms.service.MenuItemImageService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogDeletionServiceImpl implements CatalogDeletionService {
    private final CategoryRepository categories;
    private final MenuItemRepository menus;
    private final OrderItemRepository orderItems;
    private final RecipeItemRepository recipes;
    private final MenuItemImageRepository images;
    private final MenuItemImageService imageService;

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        CatalogAccess.requireOwner();
        requireId(id);
        // Lock in the same order for every deletion before removing a row or shifting positions.
        var ordered = categories.findAllForUpdateInDisplayOrder();
        var category = ordered.stream().filter(value -> id.equals(value.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (menus.existsByCategoryId(id)) throw categoryInUse();
        try {
            categories.delete(category);
            categories.flush();
        } catch (DataIntegrityViolationException ex) {
            throw categoryInUse();
        }
        var reordered = new ArrayList<Category>();
        int position = 1;
        for (var remaining : ordered) {
            if (id.equals(remaining.getId())) continue;
            if (!Integer.valueOf(position).equals(remaining.getDisplayOrder())) {
                remaining.setDisplayOrder(position);
                reordered.add(remaining);
            }
            position++;
        }
        if (!reordered.isEmpty()) categories.saveAll(reordered);
    }

    @Override
    @Transactional
    public void deleteMenuItem(Long id) {
        CatalogAccess.requireOwner();
        requireId(id);
        var item = menus.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        if (orderItems.existsByMenuItemId(id)) throw menuItemInUse();
        // Reuse the existing image lifecycle, including Cloudinary cleanup and error handling.
        for (var image : images.findCatalogImages(List.of(id))) {
            imageService.deleteImage(id, image.getId());
        }
        try {
            recipes.deleteByMenuItemId(id);
            recipes.flush();
            menus.delete(item);
            menus.flush();
        } catch (DataIntegrityViolationException ex) {
            throw menuItemInUse();
        }
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) throw new ValidationException("Catalog id must be positive");
    }

    private BusinessRuleException categoryInUse() {
        return new BusinessRuleException("CATEGORY_IN_USE", "Category still contains menu items and cannot be deleted");
    }

    private BusinessRuleException menuItemInUse() {
        return new BusinessRuleException("MENU_ITEM_IN_USE", "Menu item has existing order references and cannot be deleted");
    }
}
