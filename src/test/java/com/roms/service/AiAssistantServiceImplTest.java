package com.roms.service;

import tools.jackson.databind.ObjectMapper;
import com.roms.dto.request.AiRecommendationRequest;
import com.roms.dto.response.AiRecommendationResponse;
import com.roms.entity.Category;
import com.roms.entity.Ingredient;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.integration.GeminiClientAdapter;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.service.impl.AiAssistantServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private GeminiClientAdapter geminiClientAdapter;

    @Mock
    private MenuContextService menuContextService;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiAssistantServiceImpl aiAssistantService;

    private MenuItem phoBo;
    private MenuItem bunCha;
    private MenuItem outOfStockItem;
    private Category mainCategory;
    private Ingredient beef;
    private Ingredient pork;

    @BeforeEach
    void setUp() {
        mainCategory = new Category(1L, "Món chính", "Món chính", 1, true, null, null);

        phoBo = MenuItem.builder()
                .id(1L)
                .name("Phở bò đặc biệt")
                .category(mainCategory)
                .price(new BigDecimal("65000"))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(true)
                .build();

        bunCha = MenuItem.builder()
                .id(2L)
                .name("Bún chả Hà Nội")
                .category(mainCategory)
                .price(new BigDecimal("55000"))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(true)
                .build();

        outOfStockItem = MenuItem.builder()
                .id(3L)
                .name("Lẩu bò nhúng dấm")
                .category(mainCategory)
                .price(new BigDecimal("250000"))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(false)
                .build();

        beef = Ingredient.builder()
                .id(10L)
                .name("Thịt bò")
                .currentStock(new BigDecimal("1000"))
                .isActive(true)
                .build();

        pork = Ingredient.builder()
                .id(20L)
                .name("Thịt heo")
                .currentStock(new BigDecimal("2000"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("should fallback to featured items when Gemini is not configured")
    void shouldFallbackWhenGeminiNotConfigured() {
        when(geminiClientAdapter.isConfigured()).thenReturn(false);
        when(menuItemRepository.findFeaturedAvailableWithCategory()).thenReturn(List.of(phoBo));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(Collections.emptyList());

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .preferences("thích món nước")
                .build();

        AiRecommendationResponse response = aiAssistantService.getRecommendations(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("bảo trì");
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getName()).isEqualTo("Phở bò đặc biệt");
    }

    @Test
    @DisplayName("should parse Gemini response and validate ID and live sellability")
    void shouldReturnRecommendationsWithValidIdAndSellability() {
        when(geminiClientAdapter.isConfigured()).thenReturn(true);
        when(menuContextService.getOrBuildContext()).thenReturn("[]");

        String geminiJson = """
                ```json
                {
                  "message": "Dưới đây là các món ngon đậm đà dành cho bạn:",
                  "recommendations": [
                    { "menuItemId": 1, "reason": "Phở bò nước dùng thanh ngọt, thịt mềm" },
                    { "menuItemId": 2, "reason": "Bún chả thơm nức mũi" }
                  ]
                }
                ```
                """;
        when(geminiClientAdapter.generateText(anyString(), anyString())).thenReturn(geminiJson);
        when(menuItemRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(phoBo, bunCha));

        // Mock recipe checks: both items have sufficient stock
        RecipeItem phoRecipe = RecipeItem.builder().menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150")).build();
        RecipeItem bunChaRecipe = RecipeItem.builder().menuItem(bunCha).ingredient(pork).quantityRequired(new BigDecimal("200")).build();

        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(phoRecipe));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(2L)).thenReturn(List.of(bunChaRecipe));

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .preferences("món truyền thống")
                .partySize(2)
                .build();

        AiRecommendationResponse response = aiAssistantService.getRecommendations(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("món ngon đậm đà");
        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().get(0).getMenuItemId()).isEqualTo(1L);
        assertThat(response.getRecommendations().get(0).getIsSellable()).isTrue();
        assertThat(response.getRecommendations().get(1).getMenuItemId()).isEqualTo(2L);
        assertThat(response.getRecommendations().get(1).getIsSellable()).isTrue();
    }

    @Test
    @DisplayName("should discard out of stock or temporarily unavailable items")
    void shouldDiscardOutOfStockOrUnavailableItems() {
        when(geminiClientAdapter.isConfigured()).thenReturn(true);
        when(menuContextService.getOrBuildContext()).thenReturn("[]");

        String geminiJson = """
                {
                  "message": "Gợi ý các món bò:",
                  "recommendations": [
                    { "menuItemId": 1, "reason": "Ngon" },
                    { "menuItemId": 3, "reason": "Lẩu ấm cúng" }
                  ]
                }
                """;
        when(geminiClientAdapter.generateText(anyString(), anyString())).thenReturn(geminiJson);
        when(menuItemRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(phoBo, outOfStockItem));

        // Item 1: Sufficient stock
        RecipeItem phoRecipe = RecipeItem.builder().menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150")).build();
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(phoRecipe));

        // Item 3: Insufficient stock (requires 2000g, but only 1000g available)
        RecipeItem lauRecipe = RecipeItem.builder().menuItem(outOfStockItem).ingredient(beef).quantityRequired(new BigDecimal("2000")).build();
        when(recipeItemRepository.findByMenuItemIdWithIngredient(3L)).thenReturn(List.of(lauRecipe));

        AiRecommendationResponse response = aiAssistantService.getRecommendations(new AiRecommendationRequest());

        assertThat(response).isNotNull();
        // Item 3 must be discarded because of insufficient stock
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getMenuItemId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should discard invented or non-existing IDs from AI")
    void shouldDiscardNonExistingIds() {
        when(geminiClientAdapter.isConfigured()).thenReturn(true);
        when(menuContextService.getOrBuildContext()).thenReturn("[]");

        String geminiJson = """
                {
                  "message": "Gợi ý:",
                  "recommendations": [
                    { "menuItemId": 9999, "reason": "Món tưởng tượng" }
                  ]
                }
                """;
        when(geminiClientAdapter.generateText(anyString(), anyString())).thenReturn(geminiJson);
        when(menuItemRepository.findByIdInWithCategory(anyCollection())).thenReturn(Collections.emptyList());
        when(menuItemRepository.findFeaturedAvailableWithCategory()).thenReturn(List.of(phoBo));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(Collections.emptyList());

        AiRecommendationResponse response = aiAssistantService.getRecommendations(new AiRecommendationRequest());

        assertThat(response).isNotNull();
        // Falling back to featured because 9999 was discarded
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getMenuItemId()).isEqualTo(1L);
    }
}
