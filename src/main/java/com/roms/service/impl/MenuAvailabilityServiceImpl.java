package com.roms.service.impl;

import com.roms.dto.request.UpdateMenuStatusRequest;
import com.roms.dto.response.MenuItemSellabilityResponse;
import com.roms.dto.response.MenuItemStatusResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.security.SecurityUtils;
import com.roms.service.MenuAvailabilityService;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuAvailabilityServiceImpl implements MenuAvailabilityService {

    private final MenuItemRepository menuItemRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final StompEventPublisher stompEventPublisher;

    @Override
    @Transactional
    public MenuItemStatusResponse updateMenuStatus(Long id, UpdateMenuStatusRequest request) {
        UserRole currentRole = SecurityUtils.getCurrentUserRole()
                .orElseThrow(() -> new ForbiddenException("Authentication required"));

        if (currentRole != UserRole.OWNER && currentRole != UserRole.KITCHEN_STAFF) {
            throw new ForbiddenException("Only OWNER or KITCHEN_STAFF can update menu item status");
        }

        if (request.getStatus() == MenuItemStatus.DISCONTINUED && currentRole != UserRole.OWNER) {
            throw new ForbiddenException("Only OWNER can mark a menu item as DISCONTINUED");
        }

        MenuItem menuItem = menuItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));

        if (currentRole == UserRole.KITCHEN_STAFF && menuItem.getStatus() == MenuItemStatus.DISCONTINUED) {
            throw new ForbiddenException("Only OWNER can change a DISCONTINUED menu item");
        }

        if (request.getStatus() == MenuItemStatus.AVAILABLE) {
            List<RecipeItem> recipes = recipeItemRepository.findByMenuItemIdWithIngredient(id);
            if (recipes.isEmpty()) {
                throw new BusinessRuleException("NO_RECIPE", "Cannot resume menu item without a BOM recipe");
            }
            for (RecipeItem recipe : recipes) {
                Ingredient ingredient = recipe.getIngredient();
                BigDecimal stock = (ingredient != null && ingredient.getCurrentStock() != null)
                        ? ingredient.getCurrentStock() : BigDecimal.ZERO;
                boolean isActive = ingredient != null && Boolean.TRUE.equals(ingredient.getIsActive());
                if (!isActive || stock.compareTo(recipe.getQuantityRequired()) < 0) {
                    String ingName = ingredient != null ? ingredient.getName() : "unknown";
                    throw new BusinessRuleException("INSUFFICIENT_STOCK",
                            "Insufficient stock for ingredient [" + ingName + "] (available: " + stock + ")");
                }
            }
        }

        menuItem.setStatus(request.getStatus());
        MenuItem saved = menuItemRepository.save(menuItem);

        // Invalidate Gemini menu context cache in Redis strictly after commit
        stompEventPublisher.publishAfterCommit(StompDestinations.TOPIC_MENU, Map.of(
                "type", "MENU_STATUS_CHANGED",
                "menuItemId", saved.getId(),
                "status", saved.getStatus().name()
        ));

        return MenuItemStatusResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .status(saved.getStatus())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemSellabilityResponse checkSellability(Long id, Integer quantity) {
        int reqQty = (quantity == null || quantity < 1) ? 1 : quantity;

        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));

        List<RecipeItem> recipes = recipeItemRepository.findByMenuItemIdWithIngredient(id);
        if (recipes.isEmpty()) {
            return MenuItemSellabilityResponse.builder()
                    .menuItemId(menuItem.getId())
                    .menuItemName(menuItem.getName())
                    .status(menuItem.getStatus())
                    .sellable(false)
                    .reason("Món chưa được thiết lập định lượng nguyên liệu (BOM)")
                    .requestedQuantity(reqQty)
                    .maxPossibleQuantity(0)
                    .insufficientIngredients(Collections.emptyList())
                    .build();
        }

        List<MenuItemSellabilityResponse.InsufficientIngredientDto> insufficientList = new ArrayList<>();
        int minMaxPossible = Integer.MAX_VALUE;
        BigDecimal reqQtyBd = BigDecimal.valueOf(reqQty);

        for (RecipeItem recipe : recipes) {
            Ingredient ingredient = recipe.getIngredient();
            BigDecimal qtyRequired = recipe.getQuantityRequired();
            BigDecimal totalRequired = qtyRequired.multiply(reqQtyBd);
            BigDecimal stock = (ingredient != null && ingredient.getCurrentStock() != null)
                    ? ingredient.getCurrentStock()
                    : BigDecimal.ZERO;
            boolean isActive = ingredient != null && Boolean.TRUE.equals(ingredient.getIsActive());

            if (!isActive || stock.compareTo(totalRequired) < 0) {
                insufficientList.add(MenuItemSellabilityResponse.InsufficientIngredientDto.builder()
                        .ingredientId(ingredient != null ? ingredient.getId() : null)
                        .ingredientName(ingredient != null ? ingredient.getName() : "Unknown ingredient")
                        .unit(ingredient != null ? ingredient.getUnit() : "")
                        .requiredQuantity(totalRequired)
                        .currentStock(stock)
                        .build());
            }

            if (!isActive) {
                minMaxPossible = 0;
            } else if (qtyRequired.compareTo(BigDecimal.ZERO) > 0) {
                int possibleForThis = stock.divideToIntegralValue(qtyRequired).intValue();
                if (possibleForThis < 0) {
                    possibleForThis = 0;
                }
                minMaxPossible = Math.min(minMaxPossible, possibleForThis);
            }
        }

        Integer maxPossible = (minMaxPossible == Integer.MAX_VALUE) ? 0 : Math.max(0, minMaxPossible);

        if (menuItem.getStatus() != MenuItemStatus.AVAILABLE) {
            return MenuItemSellabilityResponse.builder()
                    .menuItemId(menuItem.getId())
                    .menuItemName(menuItem.getName())
                    .status(menuItem.getStatus())
                    .sellable(false)
                    .reason("Menu item is currently " + menuItem.getStatus().name())
                    .requestedQuantity(reqQty)
                    .maxPossibleQuantity(maxPossible)
                    .insufficientIngredients(insufficientList)
                    .build();
        }

        boolean sellable = insufficientList.isEmpty();
        String reason = sellable ? null : "Insufficient ingredient stock: " +
                insufficientList.stream()
                        .map(MenuItemSellabilityResponse.InsufficientIngredientDto::getIngredientName)
                        .collect(Collectors.joining(", "));

        return MenuItemSellabilityResponse.builder()
                .menuItemId(menuItem.getId())
                .menuItemName(menuItem.getName())
                .status(menuItem.getStatus())
                .sellable(sellable)
                .reason(reason)
                .requestedQuantity(reqQty)
                .maxPossibleQuantity(maxPossible)
                .insufficientIngredients(insufficientList)
                .build();
    }

    @Override
    @Transactional
    public void autoPauseMenuItemsWithInsufficientStock(java.util.Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return;
        }

        List<Long> affectedMenuIds = recipeItemRepository.findMenuItemIdsByIngredientIdInAndMenuItemStatus(
                ingredientIds, MenuItemStatus.AVAILABLE);

        if (affectedMenuIds.isEmpty()) {
            return;
        }

        boolean anyUpdated = false;
        for (Long menuId : affectedMenuIds) {
            MenuItemSellabilityResponse sellability = checkSellability(menuId, 1);
            if (!sellability.isSellable() || (sellability.getMaxPossibleQuantity() != null && sellability.getMaxPossibleQuantity() <= 0)) {
                MenuItem item = menuItemRepository.findById(menuId).orElse(null);
                if (item != null && item.getStatus() == MenuItemStatus.AVAILABLE) {
                    item.setStatus(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
                    menuItemRepository.save(item);
                    anyUpdated = true;
                    log.info("Auto-paused menu item id={} ({}) due to insufficient ingredient stock", item.getId(), item.getName());
                }
            }
        }

        if (anyUpdated) {
            stompEventPublisher.publishAfterCommit(StompDestinations.TOPIC_MENU, Map.of(
                    "type", "MENU_AUTO_PAUSED"
            ));
        }
    }
}
