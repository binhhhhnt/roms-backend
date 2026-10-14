package com.roms.mapper;

import com.roms.dto.response.RecipeItemResponse;
import com.roms.dto.response.RecipeResponse;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RecipeItemMapper {

    public RecipeItemResponse toItemResponse(RecipeItem recipeItem) {
        if (recipeItem == null) {
            return null;
        }

        return RecipeItemResponse.builder()
                .id(recipeItem.getId())
                .ingredientId(recipeItem.getIngredient() != null ? recipeItem.getIngredient().getId() : null)
                .ingredientName(recipeItem.getIngredient() != null ? recipeItem.getIngredient().getName() : null)
                .unit(recipeItem.getIngredient() != null ? recipeItem.getIngredient().getUnit() : null)
                .quantityRequired(recipeItem.getQuantityRequired())
                .build();
    }

    public RecipeResponse toResponse(MenuItem menuItem, List<RecipeItem> items) {
        if (menuItem == null) {
            return null;
        }

        List<RecipeItemResponse> itemResponses = items != null
                ? items.stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        return RecipeResponse.builder()
                .menuItemId(menuItem.getId())
                .menuItemName(menuItem.getName())
                .items(itemResponses)
                .build();
    }
}
