package com.roms.service;

import com.roms.dto.request.BatchUpsertRecipeRequest;
import com.roms.dto.request.UpsertRecipeItemRequest;
import com.roms.dto.response.RecipeResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.RecipeItemMapper;
import com.roms.repository.IngredientRepository;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.impl.RecipeItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeItemServiceTest {

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private MenuContextCacheService menuContextCacheService;

    @Spy
    private RecipeItemMapper recipeItemMapper = new RecipeItemMapper();

    @InjectMocks
    private RecipeItemServiceImpl recipeItemService;

    private MenuItem phoBo;
    private Ingredient beef;
    private Ingredient noodle;

    @BeforeEach
    void setUp() {
        phoBo = MenuItem.builder()
                .id(1L)
                .name("Phở Bò Đặc Biệt")
                .price(new BigDecimal("65000.00"))
                .status(MenuItemStatus.AVAILABLE)
                .build();

        beef = Ingredient.builder()
                .id(10L)
                .name("Thịt bò tái")
                .unit("g")
                .isActive(true)
                .build();

        noodle = Ingredient.builder()
                .id(11L)
                .name("Bánh phở")
                .unit("g")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Get recipe for existing menu item returns its ingredients")
    void getRecipe_Success() {
        RecipeItem item1 = RecipeItem.builder().id(101L).menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150.000")).build();
        RecipeItem item2 = RecipeItem.builder().id(102L).menuItem(phoBo).ingredient(noodle).quantityRequired(new BigDecimal("200.000")).build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));
        when(recipeItemRepository.findByMenuItemId(1L)).thenReturn(List.of(item1, item2));

        RecipeResponse response = recipeItemService.getRecipe(1L);

        assertThat(response.getMenuItemId()).isEqualTo(1L);
        assertThat(response.getMenuItemName()).isEqualTo("Phở Bò Đặc Biệt");
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getIngredientName()).isEqualTo("Thịt bò tái");
        assertThat(response.getItems().get(0).getQuantityRequired()).isEqualByComparingTo(new BigDecimal("150.000"));
    }

    @Test
    @DisplayName("Get recipe for non-existent menu item throws ResourceNotFoundException")
    void getRecipe_NotFound() {
        when(menuItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeItemService.getRecipe(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Add recipe item persists line and invalidates Redis menu context cache")
    void addRecipeItem_Success() {
        UpsertRecipeItemRequest request = UpsertRecipeItemRequest.builder()
                .ingredientId(10L)
                .quantityRequired(new BigDecimal("150.000"))
                .build();

        RecipeItem savedItem = RecipeItem.builder().id(101L).menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150.000")).build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));
        when(recipeItemRepository.existsByMenuItemIdAndIngredientId(1L, 10L)).thenReturn(false);
        when(ingredientRepository.findById(10L)).thenReturn(Optional.of(beef));
        when(recipeItemRepository.save(any(RecipeItem.class))).thenReturn(savedItem);
        when(recipeItemRepository.findByMenuItemId(1L)).thenReturn(List.of(savedItem));

        RecipeResponse response = recipeItemService.addRecipeItem(1L, request);

        assertThat(response.getItems()).hasSize(1);
        verify(recipeItemRepository).save(any(RecipeItem.class));
        verify(menuContextCacheService).invalidateMenuContext();
    }

    @Test
    @DisplayName("Add duplicate ingredient throws BusinessRuleException")
    void addRecipeItem_Duplicate_ThrowsException() {
        UpsertRecipeItemRequest request = UpsertRecipeItemRequest.builder()
                .ingredientId(10L)
                .quantityRequired(new BigDecimal("150.000"))
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));
        when(recipeItemRepository.existsByMenuItemIdAndIngredientId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> recipeItemService.addRecipeItem(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists in recipe");

        verify(recipeItemRepository, never()).save(any());
        verify(menuContextCacheService, never()).invalidateMenuContext();
    }

    @Test
    @DisplayName("Add inactive ingredient throws BusinessRuleException")
    void addRecipeItem_InactiveIngredient_ThrowsException() {
        beef.setIsActive(false);

        UpsertRecipeItemRequest request = UpsertRecipeItemRequest.builder()
                .ingredientId(10L)
                .quantityRequired(new BigDecimal("150.000"))
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));
        when(recipeItemRepository.existsByMenuItemIdAndIngredientId(1L, 10L)).thenReturn(false);
        when(ingredientRepository.findById(10L)).thenReturn(Optional.of(beef));

        assertThatThrownBy(() -> recipeItemService.addRecipeItem(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot add inactive ingredient");

        verify(recipeItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Replace recipe batch deletes old lines, saves new lines, and invalidates cache")
    void replaceRecipe_Success() {
        BatchUpsertRecipeRequest request = BatchUpsertRecipeRequest.builder()
                .items(List.of(
                        UpsertRecipeItemRequest.builder().ingredientId(10L).quantityRequired(new BigDecimal("120.000")).build(),
                        UpsertRecipeItemRequest.builder().ingredientId(11L).quantityRequired(new BigDecimal("180.000")).build()
                ))
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));
        when(ingredientRepository.findById(10L)).thenReturn(Optional.of(beef));
        when(ingredientRepository.findById(11L)).thenReturn(Optional.of(noodle));
        when(recipeItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        RecipeResponse response = recipeItemService.replaceRecipe(1L, request);

        assertThat(response.getItems()).hasSize(2);
        verify(recipeItemRepository).deleteByMenuItemId(1L);
        verify(recipeItemRepository).saveAll(anyList());
        verify(menuContextCacheService).invalidateMenuContext();
    }

    @Test
    @DisplayName("Replace recipe with duplicate ingredients in payload throws BusinessRuleException")
    void replaceRecipe_DuplicateInPayload_ThrowsException() {
        BatchUpsertRecipeRequest request = BatchUpsertRecipeRequest.builder()
                .items(List.of(
                        UpsertRecipeItemRequest.builder().ingredientId(10L).quantityRequired(new BigDecimal("120.000")).build(),
                        UpsertRecipeItemRequest.builder().ingredientId(10L).quantityRequired(new BigDecimal("180.000")).build()
                ))
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));

        assertThatThrownBy(() -> recipeItemService.replaceRecipe(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate ingredient ID in request");

        verify(recipeItemRepository, never()).deleteByMenuItemId(any());
    }

    @Test
    @DisplayName("Update recipe item quantity updates value and invalidates cache")
    void updateRecipeItem_Success() {
        RecipeItem item = RecipeItem.builder().id(101L).menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150.000")).build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(phoBo));
        when(recipeItemRepository.findByMenuItemIdAndIngredientId(1L, 10L)).thenReturn(Optional.of(item));
        when(recipeItemRepository.findByMenuItemId(1L)).thenReturn(List.of(item));

        RecipeResponse response = recipeItemService.updateRecipeItem(1L, 10L, new BigDecimal("180.000"));

        assertThat(item.getQuantityRequired()).isEqualByComparingTo(new BigDecimal("180.000"));
        verify(recipeItemRepository).save(item);
        verify(menuContextCacheService).invalidateMenuContext();
    }

    @Test
    @DisplayName("Delete recipe item deletes line and invalidates cache")
    void deleteRecipeItem_Success() {
        when(menuItemRepository.existsById(1L)).thenReturn(true);
        when(recipeItemRepository.existsByMenuItemIdAndIngredientId(1L, 10L)).thenReturn(true);

        recipeItemService.deleteRecipeItem(1L, 10L);

        verify(recipeItemRepository).deleteByMenuItemIdAndIngredientId(1L, 10L);
        verify(menuContextCacheService).invalidateMenuContext();
    }
}
