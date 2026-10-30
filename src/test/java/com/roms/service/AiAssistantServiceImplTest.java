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
                .description("Phở bò truyền thống hầm xương")
                .category(mainCategory)
                .price(new BigDecimal("65000"))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(true)
                .build();

        bunCha = MenuItem.builder()
                .id(2L)
                .name("Bún chả Hà Nội")
                .description("Bún chả nướng than hoa")
                .category(mainCategory)
                .price(new BigDecimal("55000"))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(true)
                .build();

        outOfStockItem = MenuItem.builder()
                .id(3L)
                .name("Lẩu bò nhúng dấm")
                .description("Lẩu bò tươi ngon")
                .category(mainCategory)
                .price(new BigDecimal("250000"))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(false)
                .build();

        beef = Ingredient.builder()
                .id(10L)
                .name("Thịt bò phi lê")
                .currentStock(new BigDecimal("1000"))
                .isActive(true)
                .build();

        pork = Ingredient.builder()
                .id(20L)
                .name("Thịt heo ba chỉ")
                .currentStock(new BigDecimal("2000"))
                .isActive(true)
                .build();
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

        RecipeItem phoRecipe = RecipeItem.builder().menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150")).build();
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(phoRecipe));

        RecipeItem lauRecipe = RecipeItem.builder().menuItem(outOfStockItem).ingredient(beef).quantityRequired(new BigDecimal("2000")).build();
        when(recipeItemRepository.findByMenuItemIdWithIngredient(3L)).thenReturn(List.of(lauRecipe));

        AiRecommendationResponse response = aiAssistantService.getRecommendations(new AiRecommendationRequest());

        assertThat(response).isNotNull();
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getMenuItemId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should return empty recommendations for general greeting or chit-chat")
    void shouldReturnEmptyRecommendationsForGreeting() {
        when(geminiClientAdapter.isConfigured()).thenReturn(false);
        when(menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED)).thenReturn(List.of(phoBo, bunCha));

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .message("Xin chào bạn")
                .build();

        AiRecommendationResponse response = aiAssistantService.getRecommendations(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Xin chào");
        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    @DisplayName("should return empty recommendations for restaurant info queries like opening hours or wifi")
    void shouldReturnEmptyRecommendationsForRestaurantInfo() {
        when(geminiClientAdapter.isConfigured()).thenReturn(false);
        when(menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED)).thenReturn(List.of(phoBo, bunCha));

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .message("Nhà hàng mở cửa mấy giờ?")
                .build();

        AiRecommendationResponse response = aiAssistantService.getRecommendations(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("10:00");
        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    @DisplayName("should recommend dishes based on recipe ingredients matching user query")
    void shouldRecommendDishesBasedOnRecipeIngredient() {
        when(geminiClientAdapter.isConfigured()).thenReturn(false);
        when(menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED)).thenReturn(List.of(phoBo, bunCha));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(Collections.emptyList());
        when(recipeItemRepository.findByMenuItemIdWithIngredient(2L)).thenReturn(Collections.emptyList());

        RecipeItem phoBeefRecipe = RecipeItem.builder().menuItem(phoBo).ingredient(beef).quantityRequired(new BigDecimal("150")).build();
        when(recipeItemRepository.findByMenuItemIdInWithIngredient(anyList())).thenReturn(List.of(phoBeefRecipe));

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .message("món về thịt bò")
                .build();

        AiRecommendationResponse response = aiAssistantService.getRecommendations(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getRecommendations().get(0).getName()).isEqualTo("Phở bò đặc biệt");
        assertThat(response.getRecommendations().get(0).getReason()).contains("Thịt bò");
    }

    @Test
    @DisplayName("should report not found and not recommend random dishes when ingredient does not exist")
    void shouldReportNotFoundWhenIngredientDoesNotExist() {
        when(geminiClientAdapter.isConfigured()).thenReturn(false);
        when(menuItemRepository.findByStatusNotWithCategory(MenuItemStatus.DISCONTINUED)).thenReturn(List.of(phoBo, bunCha));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(Collections.emptyList());
        when(recipeItemRepository.findByMenuItemIdWithIngredient(2L)).thenReturn(Collections.emptyList());
        when(recipeItemRepository.findByMenuItemIdInWithIngredient(anyList())).thenReturn(Collections.emptyList());

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .message("nhà hàng có món cá hồi không?")
                .build();

        AiRecommendationResponse response = aiAssistantService.getRecommendations(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("chưa có món ăn");
        assertThat(response.getRecommendations()).isEmpty();
    }
}
