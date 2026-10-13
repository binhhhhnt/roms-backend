package com.roms.service;

import com.roms.dto.request.UpdateMenuStatusRequest;
import com.roms.dto.response.MenuItemSellabilityResponse;
import com.roms.dto.response.MenuItemStatusResponse;
import com.roms.entity.Ingredient;
import com.roms.entity.MenuItem;
import com.roms.entity.RecipeItem;
import com.roms.enums.MenuItemStatus;
import com.roms.enums.UserRole;
import com.roms.exception.ForbiddenException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.MenuItemRepository;
import com.roms.repository.RecipeItemRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.MenuAvailabilityServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuAvailabilityServiceImplTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Mock
    private MenuContextCacheService menuContextCacheService;

    @InjectMocks
    private MenuAvailabilityServiceImpl menuAvailabilityService;

    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        testMenuItem = MenuItem.builder()
                .id(1L)
                .name("Phở Bò Đặc Biệt")
                .price(BigDecimal.valueOf(65000))
                .status(MenuItemStatus.AVAILABLE)
                .isFeatured(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(10L)
                .username("test_user")
                .role(role)
                .active(true)
                .build();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("OWNER can update menu item status to TEMPORARILY_UNAVAILABLE and invalidates cache")
    void shouldUpdateStatusAsOwner() {
        authenticateUser(UserRole.OWNER);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        MenuItemStatusResponse response = menuAvailabilityService.updateMenuStatus(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        assertThat(testMenuItem.getStatus()).isEqualTo(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        verify(menuItemRepository).save(testMenuItem);
        verify(menuContextCacheService).invalidateMenuContext();
    }

    @Test
    @DisplayName("OWNER can update menu item status to DISCONTINUED")
    void shouldUpdateStatusToDiscontinuedAsOwner() {
        authenticateUser(UserRole.OWNER);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest(MenuItemStatus.DISCONTINUED);
        MenuItemStatusResponse response = menuAvailabilityService.updateMenuStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo(MenuItemStatus.DISCONTINUED);
        verify(menuContextCacheService).invalidateMenuContext();
    }

    @Test
    @DisplayName("KITCHEN_STAFF can update menu item status to TEMPORARILY_UNAVAILABLE")
    void shouldUpdateStatusAsKitchenStaff() {
        authenticateUser(UserRole.KITCHEN_STAFF);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        MenuItemStatusResponse response = menuAvailabilityService.updateMenuStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        verify(menuContextCacheService).invalidateMenuContext();
    }

    @Test
    @DisplayName("KITCHEN_STAFF cannot mark menu item as DISCONTINUED (throws 403 Forbidden)")
    void shouldThrowForbiddenWhenKitchenStaffMarksDiscontinued() {
        authenticateUser(UserRole.KITCHEN_STAFF);

        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest(MenuItemStatus.DISCONTINUED);

        assertThatThrownBy(() -> menuAvailabilityService.updateMenuStatus(1L, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only OWNER can mark a menu item as DISCONTINUED");

        verify(menuItemRepository, never()).save(any());
        verify(menuContextCacheService, never()).invalidateMenuContext();
    }

    @Test
    @DisplayName("SERVICE_STAFF cannot update menu status (throws 403 Forbidden)")
    void shouldThrowForbiddenWhenServiceStaffUpdatesStatus() {
        authenticateUser(UserRole.SERVICE_STAFF);

        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest(MenuItemStatus.AVAILABLE);

        assertThatThrownBy(() -> menuAvailabilityService.updateMenuStatus(1L, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only OWNER or KITCHEN_STAFF");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when menu item does not exist")
    void shouldThrowNotFoundWhenUpdatingNonExistentMenuItem() {
        authenticateUser(UserRole.OWNER);
        when(menuItemRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest(MenuItemStatus.AVAILABLE);

        assertThatThrownBy(() -> menuAvailabilityService.updateMenuStatus(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Menu item not found");
    }

    @Test
    @DisplayName("checkSellability: when status is TEMPORARILY_UNAVAILABLE, returns sellable=false without recipe check")
    void shouldReturnNotSellableWhenStatusIsTemporarilyUnavailable() {
        testMenuItem.setStatus(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));

        MenuItemSellabilityResponse result = menuAvailabilityService.checkSellability(1L, 1);

        assertThat(result.isSellable()).isFalse();
        assertThat(result.getStatus()).isEqualTo(MenuItemStatus.TEMPORARILY_UNAVAILABLE);
        assertThat(result.getMaxPossibleQuantity()).isEqualTo(0);
        assertThat(result.getReason()).contains("TEMPORARILY_UNAVAILABLE");
        verifyNoInteractions(recipeItemRepository);
    }

    @Test
    @DisplayName("checkSellability: when status is DISCONTINUED, returns sellable=false")
    void shouldReturnNotSellableWhenStatusIsDiscontinued() {
        testMenuItem.setStatus(MenuItemStatus.DISCONTINUED);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));

        MenuItemSellabilityResponse result = menuAvailabilityService.checkSellability(1L, 1);

        assertThat(result.isSellable()).isFalse();
        assertThat(result.getStatus()).isEqualTo(MenuItemStatus.DISCONTINUED);
        assertThat(result.getMaxPossibleQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("checkSellability: when item has no recipes, returns sellable=true with unlimited capacity")
    void shouldReturnSellableWhenItemHasNoRecipes() {
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(Collections.emptyList());

        MenuItemSellabilityResponse result = menuAvailabilityService.checkSellability(1L, 2);

        assertThat(result.isSellable()).isTrue();
        assertThat(result.getReason()).isNull();
        assertThat(result.getMaxPossibleQuantity()).isNull();
    }

    @Test
    @DisplayName("checkSellability: when stock is sufficient for requested quantity, returns sellable=true and max capacity")
    void shouldReturnSellableWhenStockIsSufficient() {
        Ingredient beef = Ingredient.builder()
                .id(101L)
                .name("Thịt Bò")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(5.0))
                .isActive(true)
                .build();
        RecipeItem recipeBeef = RecipeItem.builder()
                .id(1L)
                .menuItem(testMenuItem)
                .ingredient(beef)
                .quantityRequired(BigDecimal.valueOf(0.200)) // 5.0 / 0.2 = 25 dishes max
                .build();

        Ingredient noodles = Ingredient.builder()
                .id(102L)
                .name("Bánh Phở")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(3.0))
                .isActive(true)
                .build();
        RecipeItem recipeNoodles = RecipeItem.builder()
                .id(2L)
                .menuItem(testMenuItem)
                .ingredient(noodles)
                .quantityRequired(BigDecimal.valueOf(0.300)) // 3.0 / 0.3 = 10 dishes max
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(recipeBeef, recipeNoodles));

        MenuItemSellabilityResponse result = menuAvailabilityService.checkSellability(1L, 4);

        assertThat(result.isSellable()).isTrue();
        assertThat(result.getReason()).isNull();
        assertThat(result.getMaxPossibleQuantity()).isEqualTo(10);
        assertThat(result.getInsufficientIngredients()).isEmpty();
    }

    @Test
    @DisplayName("checkSellability: when stock is insufficient for requested quantity, returns sellable=false with details")
    void shouldReturnNotSellableWhenStockIsInsufficient() {
        Ingredient beef = Ingredient.builder()
                .id(101L)
                .name("Thịt Bò")
                .unit("kg")
                .currentStock(BigDecimal.valueOf(0.300))
                .isActive(true)
                .build();
        RecipeItem recipeBeef = RecipeItem.builder()
                .id(1L)
                .menuItem(testMenuItem)
                .ingredient(beef)
                .quantityRequired(BigDecimal.valueOf(0.200)) // needs 0.400 for 2 dishes -> insufficient!
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(recipeBeef));

        MenuItemSellabilityResponse result = menuAvailabilityService.checkSellability(1L, 2);

        assertThat(result.isSellable()).isFalse();
        assertThat(result.getReason()).contains("Insufficient ingredient stock").contains("Thịt Bò");
        assertThat(result.getMaxPossibleQuantity()).isEqualTo(1);
        assertThat(result.getInsufficientIngredients()).hasSize(1);
        assertThat(result.getInsufficientIngredients().get(0).getIngredientName()).isEqualTo("Thịt Bò");
    }

    @Test
    @DisplayName("checkSellability: when an ingredient is inactive, returns sellable=false and maxPossibleQuantity=0")
    void shouldReturnNotSellableWhenIngredientIsInactive() {
        Ingredient seasoning = Ingredient.builder()
                .id(103L)
                .name("Gia vị đặc biệt")
                .unit("g")
                .currentStock(BigDecimal.valueOf(100))
                .isActive(false) // inactive
                .build();
        RecipeItem recipe = RecipeItem.builder()
                .id(3L)
                .menuItem(testMenuItem)
                .ingredient(seasoning)
                .quantityRequired(BigDecimal.valueOf(10))
                .build();

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(recipeItemRepository.findByMenuItemIdWithIngredient(1L)).thenReturn(List.of(recipe));

        MenuItemSellabilityResponse result = menuAvailabilityService.checkSellability(1L, 1);

        assertThat(result.isSellable()).isFalse();
        assertThat(result.getMaxPossibleQuantity()).isEqualTo(0);
        assertThat(result.getInsufficientIngredients()).hasSize(1);
    }
}
