package com.roms.service;

import tools.jackson.databind.ObjectMapper;
import com.roms.entity.Category;
import com.roms.entity.Ingredient;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.impl.MenuContextServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuContextServiceImplTest {

    @Mock
    private MenuContextCacheService menuContextCacheService;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MenuContextServiceImpl menuContextService;

    private MenuItem phoBo;
    private Category mainCategory;
    private Ingredient beef;

    @BeforeEach
    void setUp() {
        mainCategory = new Category(1L, "Món chính", "Món chính", 1, true, null, null);
        phoBo = MenuItem.builder()
                .id(1L)
                .name("Phở bò đặc biệt")
                .category(mainCategory)
                .price(new BigDecimal("65000"))
                .description("Phở bò thơm ngon")
                .status(MenuItemStatus.AVAILABLE)
                .build();

        beef = Ingredient.builder()
                .id(10L)
                .name("Thịt bò")
                .unit("g")
                .currentStock(new BigDecimal("5000"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("should return cached context when cache hit occurs")
    void shouldReturnCachedContextWhenCacheHit() {
        String cachedJson = "[{\"id\":1,\"name\":\"Phở bò đặc biệt\"}]";
        when(menuContextCacheService.getMenuContext()).thenReturn(Optional.of(cachedJson));

        String result = menuContextService.getOrBuildContext();

        assertThat(result).isEqualTo(cachedJson);
        verify(menuItemRepository, never()).findByStatusNotWithCategory(any());
        verify(menuContextCacheService, never()).saveMenuContext(anyString());
    }

    @Test
    @DisplayName("should rebuild from database and cache when cache miss occurs")
    void shouldRebuildFromDatabaseWhenCacheMiss() {
        when(menuContextCacheService.getMenuContext()).thenReturn(Optional.empty());
        when(menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED))
                .thenReturn(List.of(phoBo));

        RecipeItem recipeItem = RecipeItem.builder()
                .id(100L)
                .menuItem(phoBo)
                .ingredient(beef)
                .quantityRequired(new BigDecimal("150"))
                .build();

        when(recipeItemRepository.findByMenuItemIdInWithIngredient(List.of(1L)))
                .thenReturn(List.of(recipeItem));

        String result = menuContextService.getOrBuildContext();

        assertThat(result).isNotNull();
        assertThat(result).contains("Phở bò đặc biệt");
        assertThat(result).contains("Thịt bò");
        assertThat(result).contains("Món chính");

        verify(menuContextCacheService).saveMenuContext(result);
    }

    @Test
    @DisplayName("should invalidate cache when requested")
    void shouldInvalidateCache() {
        menuContextService.invalidateContext();
        verify(menuContextCacheService).invalidateMenuContext();
    }
}
