package com.roms.service.impl;

import com.roms.dto.request.BatchUpsertRecipeRequest;
import com.roms.dto.request.UpsertRecipeItemRequest;
import com.roms.dto.response.RecipeResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.RecipeItemMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.MenuContextCacheService;
import com.roms.service.RecipeItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class RecipeItemServiceImpl implements RecipeItemService {

    private final RecipeItemRepository recipeItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final IngredientRepository ingredientRepository;
    private final MenuContextCacheService menuContextCacheService;
    private final RecipeItemMapper recipeItemMapper;

    public RecipeItemServiceImpl(
            RecipeItemRepository recipeItemRepository,
            MenuItemRepository menuItemRepository,
            IngredientRepository ingredientRepository,
            MenuContextCacheService menuContextCacheService,
            RecipeItemMapper recipeItemMapper) {
        this.recipeItemRepository = recipeItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.ingredientRepository = ingredientRepository;
        this.menuContextCacheService = menuContextCacheService;
        this.recipeItemMapper = recipeItemMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(Long menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found with id: " + menuItemId));
        List<RecipeItem> items = recipeItemRepository.findByMenuItemId(menuItemId);
        return recipeItemMapper.toResponse(menuItem, items);
    }

    @Override
    @Transactional
    public RecipeResponse replaceRecipe(Long menuItemId, BatchUpsertRecipeRequest request) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found with id: " + menuItemId));

        Set<Long> seen = new HashSet<>();
        for (UpsertRecipeItemRequest itemReq : request.getItems()) {
            if (!seen.add(itemReq.getIngredientId())) {
                throw new BusinessRuleException("DUPLICATE_RECIPE_ITEM",
                        "Duplicate ingredient ID in request: " + itemReq.getIngredientId());
            }
        }

        List<RecipeItem> newItems = new ArrayList<>();
        for (UpsertRecipeItemRequest itemReq : request.getItems()) {
            Ingredient ingredient = ingredientRepository.findById(itemReq.getIngredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + itemReq.getIngredientId()));

            if (!Boolean.TRUE.equals(ingredient.getIsActive())) {
                throw new BusinessRuleException("INACTIVE_INGREDIENT",
                        "Cannot add inactive ingredient to recipe: " + ingredient.getName());
            }

            newItems.add(RecipeItem.builder()
                    .menuItem(menuItem)
                    .ingredient(ingredient)
                    .quantityRequired(itemReq.getQuantityRequired())
                    .build());
        }

        recipeItemRepository.deleteByMenuItemId(menuItemId);
        List<RecipeItem> savedItems = recipeItemRepository.saveAll(newItems);

        // Invalidate Redis Gemini menu context cache
        menuContextCacheService.invalidateMenuContext();

        log.info("Replaced recipe for menuItemId={} with {} items", menuItemId, savedItems.size());
        return recipeItemMapper.toResponse(menuItem, savedItems);
    }

    @Override
    @Transactional
    public RecipeResponse addRecipeItem(Long menuItemId, UpsertRecipeItemRequest request) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found with id: " + menuItemId));

        if (recipeItemRepository.existsByMenuItemIdAndIngredientId(menuItemId, request.getIngredientId())) {
            throw new BusinessRuleException("DUPLICATE_RECIPE_ITEM",
                    "Ingredient already exists in recipe for this menu item");
        }

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + request.getIngredientId()));

        if (!Boolean.TRUE.equals(ingredient.getIsActive())) {
            throw new BusinessRuleException("INACTIVE_INGREDIENT",
                    "Cannot add inactive ingredient to recipe: " + ingredient.getName());
        }

        RecipeItem item = RecipeItem.builder()
                .menuItem(menuItem)
                .ingredient(ingredient)
                .quantityRequired(request.getQuantityRequired())
                .build();
        recipeItemRepository.save(item);

        // Invalidate Redis Gemini menu context cache
        menuContextCacheService.invalidateMenuContext();

        List<RecipeItem> items = recipeItemRepository.findByMenuItemId(menuItemId);
        log.info("Added ingredient id={} to recipe of menuItemId={}", request.getIngredientId(), menuItemId);
        return recipeItemMapper.toResponse(menuItem, items);
    }

    @Override
    @Transactional
    public RecipeResponse updateRecipeItem(Long menuItemId, Long ingredientId, BigDecimal quantityRequired) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found with id: " + menuItemId));

        RecipeItem item = recipeItemRepository.findByMenuItemIdAndIngredientId(menuItemId, ingredientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe item not found for menuItemId: " + menuItemId + " and ingredientId: " + ingredientId));

        if (quantityRequired == null || quantityRequired.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Quantity required must be greater than 0");
        }

        item.setQuantityRequired(quantityRequired);
        recipeItemRepository.save(item);

        // Invalidate Redis Gemini menu context cache
        menuContextCacheService.invalidateMenuContext();

        List<RecipeItem> items = recipeItemRepository.findByMenuItemId(menuItemId);
        log.info("Updated quantityRequired for ingredient id={} in recipe of menuItemId={}", ingredientId, menuItemId);
        return recipeItemMapper.toResponse(menuItem, items);
    }

    @Override
    @Transactional
    public void deleteRecipeItem(Long menuItemId, Long ingredientId) {
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new ResourceNotFoundException("MenuItem not found with id: " + menuItemId);
        }

        if (!recipeItemRepository.existsByMenuItemIdAndIngredientId(menuItemId, ingredientId)) {
            throw new ResourceNotFoundException("Recipe item not found for menuItemId: " + menuItemId + " and ingredientId: " + ingredientId);
        }

        recipeItemRepository.deleteByMenuItemIdAndIngredientId(menuItemId, ingredientId);

        // Invalidate Redis Gemini menu context cache
        menuContextCacheService.invalidateMenuContext();

        log.info("Deleted ingredient id={} from recipe of menuItemId={}", ingredientId, menuItemId);
    }
}
