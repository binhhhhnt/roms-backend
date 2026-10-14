package com.roms.service.impl;

import com.roms.dto.request.CreateIngredientRequest;
import com.roms.dto.request.UpdateIngredientRequest;
import com.roms.dto.response.IngredientResponse;
import com.roms.entity.Ingredient;
import com.roms.enums.MenuItemStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.IngredientMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.IngredientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final IngredientMapper ingredientMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<IngredientResponse> getIngredients(Boolean isActive, Pageable pageable) {
        Page<Ingredient> page = (isActive != null)
                ? ingredientRepository.findByIsActive(isActive, pageable)
                : ingredientRepository.findAll(pageable);
        return page.map(ingredientMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IngredientResponse getIngredientById(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + id));
        return ingredientMapper.toResponse(ingredient);
    }

    @Override
    @Transactional
    public IngredientResponse createIngredient(CreateIngredientRequest request) {
        String trimmedName = request.getName().trim();
        if (ingredientRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new BusinessRuleException("DUPLICATE_INGREDIENT_NAME",
                    "Ingredient with name '" + trimmedName + "' already exists");
        }

        Ingredient ingredient = ingredientMapper.toEntity(request);
        Ingredient saved = ingredientRepository.save(ingredient);
        log.info("Created ingredient id={}, name={}", saved.getId(), saved.getName());
        return ingredientMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public IngredientResponse updateIngredient(Long id, UpdateIngredientRequest request) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + id));

        String trimmedName = request.getName().trim();
        if (ingredientRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new BusinessRuleException("DUPLICATE_INGREDIENT_NAME",
                    "Ingredient with name '" + trimmedName + "' already exists");
        }

        ingredient.setName(trimmedName);
        ingredient.setUnit(request.getUnit().trim());
        ingredient.setMinimumStock(request.getMinimumStock());

        Ingredient updated = ingredientRepository.save(ingredient);
        log.info("Updated ingredient id={}", updated.getId());
        return ingredientMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public IngredientResponse deactivateIngredient(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found with id: " + id));

        if (!Boolean.TRUE.equals(ingredient.getIsActive())) {
            return ingredientMapper.toResponse(ingredient);
        }

        // Rule: Do not allow deactivating if used in recipes of non-DISCONTINUED menu items
        boolean inUseInActiveMenuItems = recipeItemRepository.existsByIngredientIdAndMenuItemStatusNot(
                id, MenuItemStatus.DISCONTINUED);
        if (inUseInActiveMenuItems) {
            throw new BusinessRuleException("INGREDIENT_IN_USE",
                    "Cannot deactivate ingredient because it is used in recipes of active menu items");
        }

        ingredient.setIsActive(false);
        Ingredient saved = ingredientRepository.save(ingredient);
        log.info("Deactivated ingredient id={}", saved.getId());
        return ingredientMapper.toResponse(saved);
    }
}
