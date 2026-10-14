package com.roms.service;

import com.roms.dto.request.BatchUpsertRecipeRequest;
import com.roms.dto.request.UpsertRecipeItemRequest;
import com.roms.dto.response.RecipeResponse;

import java.math.BigDecimal;

public interface RecipeItemService {

    RecipeResponse getRecipe(Long menuItemId);

    RecipeResponse replaceRecipe(Long menuItemId, BatchUpsertRecipeRequest request);

    RecipeResponse addRecipeItem(Long menuItemId, UpsertRecipeItemRequest request);

    RecipeResponse updateRecipeItem(Long menuItemId, Long ingredientId, BigDecimal quantityRequired);

    void deleteRecipeItem(Long menuItemId, Long ingredientId);
}
