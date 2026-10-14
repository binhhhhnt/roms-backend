package com.roms.service;

import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.dto.response.MenuItemSellabilityResponse;
import com.roms.exception.*;
import com.roms.mapper.*;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuItemReadServiceTest {
    private MenuItemRepository menus;
    private MenuItemImageRepository images;
    private MenuAvailabilityService availability;
    private MenuItemService service;
    private final Pageable page = PageRequest.of(0, 20, Sort.by("name").and(Sort.by("id")));
    @BeforeEach void setUp() {
        menus = mock(MenuItemRepository.class); images = mock(MenuItemImageRepository.class);
        availability = mock(MenuAvailabilityService.class);
        service = new MenuItemServiceImpl(menus, mock(CategoryRepository.class), new MenuItemMapper(),
                mock(MenuContextInvalidator.class), images, new MenuItemImageMapper(), new CategoryMapper(), availability);
        authenticate(UserRole.TABLE_ACCOUNT);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(UserRole role) {
        var user = UserPrincipal.builder().id(1L).username("reader").role(role).active(true).tableId(1L).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    private MenuItem item(Long id, boolean activeCategory, MenuItemStatus status) {
        return MenuItem.builder().id(id).category(Category.builder().id(3L).name("Main").isActive(activeCategory).displayOrder(1).build())
                .name("Soup").price(new BigDecimal("50000.00")).isFeatured(true).status(status).build();
    }
    private MenuItemSellabilityResponse sellability(boolean sellable, int quantity) {
        return MenuItemSellabilityResponse.builder().menuItemId(8L).sellable(sellable).requestedQuantity(quantity)
                .reason(sellable ? null : "Insufficient stock").maxPossibleQuantity(sellable ? 10 : 0).build();
    }
    @Test void tabletListUsesDatabaseVisibilityBeforePaginationAndBatchImages() {
        var item = item(8L, true, MenuItemStatus.AVAILABLE);
        when(menus.findCatalog(3L, true, page)).thenReturn(new PageImpl<>(List.of(item), page, 1));
        var first = MenuItemImage.builder().id(11L).menuItem(item).displayOrder(0).imageUrl("https://example.com/1").build();
        var second = MenuItemImage.builder().id(12L).menuItem(item).displayOrder(1).imageUrl("https://example.com/2").build();
        when(images.findCatalogImages(List.of(8L))).thenReturn(List.of(first, second));
        when(availability.checkSellability(8L, 2)).thenReturn(sellability(false, 2));
        var result = service.getMenuItems(3L, 2, PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).singleElement().satisfies(response -> {
            assertThat(response.getCategory().getName()).isEqualTo("Main");
            assertThat(response.getImages()).extracting(value -> value.getId()).containsExactly(11L, 12L);
            assertThat(response.getAvailability().isSellable()).isFalse();
            assertThat(response.getAvailability().getRequestedQuantity()).isEqualTo(2);
            assertThat(response.getPrice()).isEqualByComparingTo("50000.00");
        });
        verify(images, times(1)).findCatalogImages(any());
    }
    @Test void allStaffRolesReadDiscontinuedAndInactiveCategory() {
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(item(8L, false, MenuItemStatus.DISCONTINUED)));
        when(images.findCatalogImages(any())).thenReturn(List.of());
        when(availability.checkSellability(8L, 1)).thenReturn(sellability(false, 1));
        for (UserRole role : List.of(UserRole.OWNER, UserRole.SERVICE_STAFF, UserRole.KITCHEN_STAFF, UserRole.INVENTORY_STAFF)) {
            authenticate(role);
            assertThat(service.getMenuItem(8L, 1).getStatus()).isEqualTo(MenuItemStatus.DISCONTINUED);
        }
    }
    @Test void tabletHiddenAndMissingItemsReturnSame404WithoutAvailabilityOrImageLookup() {
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(item(8L, false, MenuItemStatus.AVAILABLE)));
        assertThatThrownBy(() -> service.getMenuItem(8L, 1)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Menu item not found");
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(item(8L, true, MenuItemStatus.DISCONTINUED)));
        assertThatThrownBy(() -> service.getMenuItem(8L, 1)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Menu item not found");
        when(menus.findCatalogById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMenuItem(8L, 1)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Menu item not found");
        verifyNoInteractions(images, availability);
    }
    @Test void temporaryUnavailableRemainsVisibleAndAvailabilityIsRecheckedEveryRead() {
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(item(8L, true, MenuItemStatus.TEMPORARILY_UNAVAILABLE)));
        when(images.findCatalogImages(any())).thenReturn(List.of());
        when(availability.checkSellability(8L, 1)).thenReturn(sellability(false, 1));
        assertThat(service.getMenuItem(8L, 1).getAvailability().isSellable()).isFalse();
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(item(8L, true, MenuItemStatus.AVAILABLE)));
        when(availability.checkSellability(8L, 1)).thenReturn(sellability(true, 1));
        assertThat(service.getMenuItem(8L, 1).getAvailability().isSellable()).isTrue();
        verify(availability, times(2)).checkSellability(8L, 1);
    }
    @Test void staffListCapsSizeAndAppendsStableIdSort() {
        authenticate(UserRole.OWNER);
        var normalized = PageRequest.of(2, 100, Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")));
        when(menus.findCatalog(null, false, normalized)).thenReturn(new PageImpl<>(List.of(), normalized, 0));
        var result = service.getMenuItems(null, 1, PageRequest.of(2, 500, Sort.by(Sort.Order.desc("price"))));
        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getPage()).isEqualTo(2);
        verifyNoInteractions(images, availability);
    }
    @Test void anonymousAndInvalidInputsNeverQuery() {
        assertThatThrownBy(() -> service.getMenuItems(0L, 1, page)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.getMenuItem(0L, 1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.getMenuItem(8L, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.getMenuItems(null, 1, PageRequest.of(0, 20, Sort.by("category.passwordHash"))))
                .isInstanceOf(ValidationException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.getMenuItem(8L, 1)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.getMenuItems(null, 1, page)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(menus, images, availability);
    }
    @Test void groupsImagesPerMenuAndUsesEmptyArrayForNoImages() {
        var first = item(8L, true, MenuItemStatus.AVAILABLE); var second = item(9L, true, MenuItemStatus.AVAILABLE);
        when(menus.findCatalog(null, true, page)).thenReturn(new PageImpl<>(List.of(first, second), page, 2));
        when(images.findCatalogImages(List.of(8L, 9L))).thenReturn(List.of(MenuItemImage.builder().id(12L).menuItem(second).imageUrl("https://example.com/2").displayOrder(0).build()));
        when(availability.checkSellability(anyLong(), eq(1))).thenReturn(sellability(true, 1));
        var result = service.getMenuItems(null, 1, page);
        assertThat(result.getContent().get(0).getImages()).isEmpty();
        assertThat(result.getContent().get(1).getImages()).singleElement().satisfies(image -> assertThat(image.getMenuItemId()).isEqualTo(9L));
    }

    @Test void integratesActualBe008LogicAndReflectsChangedIngredientStock() {
        var recipeRepository = mock(RecipeItemRepository.class);
        var liveAvailability = new MenuAvailabilityServiceImpl(menus, recipeRepository, mock(MenuContextCacheService.class));
        var liveService = new MenuItemServiceImpl(menus, mock(CategoryRepository.class), new MenuItemMapper(),
                mock(MenuContextInvalidator.class), images, new MenuItemImageMapper(), new CategoryMapper(), liveAvailability);
        var menu = item(8L, true, MenuItemStatus.AVAILABLE);
        var ingredient = Ingredient.builder().id(5L).name("Ingredient").unit("kg").isActive(true).currentStock(new BigDecimal("10")).build();
        var recipe = RecipeItem.builder().menuItem(menu).ingredient(ingredient).quantityRequired(new BigDecimal("2")).build();
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(menu));
        when(menus.findById(8L)).thenReturn(Optional.of(menu));
        when(images.findCatalogImages(List.of(8L))).thenReturn(List.of());
        when(recipeRepository.findByMenuItemIdWithIngredient(8L)).thenReturn(List.of(recipe));
        var firstRead = liveService.getMenuItem(8L, 3);
        assertThat(firstRead.getAvailability().isSellable()).isTrue();
        assertThat(firstRead.getAvailability().getMaxPossibleQuantity()).isEqualTo(5);
        ingredient.setCurrentStock(new BigDecimal("4"));
        var secondRead = liveService.getMenuItem(8L, 3);
        assertThat(secondRead.getAvailability().isSellable()).isFalse();
        assertThat(secondRead.getAvailability().getRequestedQuantity()).isEqualTo(3);
        assertThat(secondRead.getAvailability().getMaxPossibleQuantity()).isEqualTo(2);
        verify(recipeRepository, times(2)).findByMenuItemIdWithIngredient(8L);
    }
}
