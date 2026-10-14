package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.BatchUpsertRecipeRequest;
import com.roms.dto.request.UpdateRecipeItemQuantityRequest;
import com.roms.dto.request.UpsertRecipeItemRequest;
import com.roms.dto.response.RecipeResponse;
import com.roms.service.RecipeItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/menu-items/{menuItemId}/recipe")
@RequiredArgsConstructor
public class RecipeItemController {

    private final RecipeItemService recipeItemService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'KITCHEN_STAFF', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<RecipeResponse>> getRecipe(@PathVariable Long menuItemId) {
        var response = recipeItemService.getRecipe(menuItemId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<RecipeResponse>> replaceRecipe(
            @PathVariable Long menuItemId,
            @Valid @RequestBody BatchUpsertRecipeRequest request) {
        var response = recipeItemService.replaceRecipe(menuItemId, request);
        return ResponseEntity.ok(ApiResponse.success("Recipe updated successfully", response));
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<RecipeResponse>> addRecipeItem(
            @PathVariable Long menuItemId,
            @Valid @RequestBody UpsertRecipeItemRequest request) {
        var response = recipeItemService.addRecipeItem(menuItemId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recipe item added successfully", response));
    }

    @PatchMapping("/items/{ingredientId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateRecipeItem(
            @PathVariable Long menuItemId,
            @PathVariable Long ingredientId,
            @Valid @RequestBody UpdateRecipeItemQuantityRequest request) {
        var response = recipeItemService.updateRecipeItem(menuItemId, ingredientId, request.getQuantityRequired());
        return ResponseEntity.ok(ApiResponse.success("Recipe item updated successfully", response));
    }

    @DeleteMapping("/items/{ingredientId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteRecipeItem(
            @PathVariable Long menuItemId,
            @PathVariable Long ingredientId) {
        recipeItemService.deleteRecipeItem(menuItemId, ingredientId);
        return ResponseEntity.ok(ApiResponse.success("Recipe item deleted successfully", null));
    }
}
