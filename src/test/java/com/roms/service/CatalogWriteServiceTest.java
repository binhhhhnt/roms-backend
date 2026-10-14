package com.roms.service;

import com.roms.dto.request.*;
import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.exception.*;
import com.roms.mapper.*;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogWriteServiceTest {
    private CategoryRepository categories;
    private MenuItemRepository menus;
    private MenuContextCacheService cache;
    private CategoryService categoryService;
    private MenuItemService menuService;

    @BeforeEach void setUp() {
        categories = mock(CategoryRepository.class);
        menus = mock(MenuItemRepository.class);
        cache = mock(MenuContextCacheService.class);
        var invalidator = new MenuContextInvalidator(cache);
        categoryService = new CategoryServiceImpl(categories, new CategoryMapper(), invalidator);
        menuService = new MenuItemServiceImpl(menus, categories, new MenuItemMapper(), invalidator,
                mock(MenuItemImageRepository.class), new MenuItemImageMapper(), new CategoryMapper(), mock(MenuAvailabilityService.class));
        TransactionSynchronizationManager.initSynchronization();
        authenticate(UserRole.OWNER);
    }
    @AfterEach void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        SecurityContextHolder.clearContext();
    }
    private void authenticate(UserRole role) {
        var user = UserPrincipal.builder().id(1L).username("user").role(role).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    private CategoryWriteRequest categoryRequest() {
        var request = new CategoryWriteRequest();
        request.setName(" Main "); request.setDescription("food"); request.setDisplayOrder(1); request.setIsActive(false);
        return request;
    }
    private MenuItemWriteRequest menuRequest() {
        var request = new MenuItemWriteRequest();
        request.setCategoryId(3L); request.setName(" Soup "); request.setPrice(new BigDecimal("50000.00")); request.setIsFeatured(true);
        return request;
    }
    @Test void categoryCreateMapsFieldsAndInvalidatesOnlyAfterCommit() {
        when(categories.save(any())).thenAnswer(call -> { Category category = call.getArgument(0); category.setId(3L); return category; });
        var response = categoryService.createCategory(categoryRequest());
        assertThat(response.getName()).isEqualTo("Main");
        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getId()).isEqualTo(3L);
        verifyNoInteractions(cache);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache).invalidateMenuContext();
    }
    @Test void categoryUpdateCanDeactivateWithoutDeleting() {
        var category = Category.builder().id(3L).name("Old").isActive(true).build();
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(categories.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(categoryService.updateCategory(3L, categoryRequest()).getIsActive()).isFalse();
        verify(categories, never()).delete(any());
    }
    @Test void menuCreateDefaultsToAvailableAndKeepsPrecisePrice() {
        when(categories.findById(3L)).thenReturn(Optional.of(Category.builder().id(3L).build()));
        when(menus.save(any())).thenAnswer(call -> { MenuItem menu = call.getArgument(0); menu.setId(8L); return menu; });
        var response = menuService.createMenuItem(menuRequest());
        assertThat(response.getStatus()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(response.getPrice()).isEqualByComparingTo("50000.00");
        assertThat(response.getCategoryId()).isEqualTo(3L);
        assertThat(response.getName()).isEqualTo("Soup");
        assertThat(response.getIsFeatured()).isTrue();
    }
    @Test void updatePreservesStatusAndDoesNotTouchOrderHistory() {
        var menu = MenuItem.builder().id(8L).status(MenuItemStatus.DISCONTINUED).build();
        when(menus.findByIdForUpdate(8L)).thenReturn(Optional.of(menu));
        when(categories.findById(3L)).thenReturn(Optional.of(Category.builder().id(3L).build()));
        when(menus.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(menuService.updateMenuItem(8L, menuRequest()).getStatus()).isEqualTo(MenuItemStatus.DISCONTINUED);
        verify(menus).findByIdForUpdate(8L);
        verify(menus, never()).delete(any());
    }
    @Test void allNonOwnerRolesAreRejectedBeforePersistence() {
        for (var role : UserRole.values()) {
            if (role == UserRole.OWNER) continue;
            authenticate(role);
            assertThatThrownBy(() -> categoryService.createCategory(categoryRequest())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> categoryService.updateCategory(3L, categoryRequest())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> menuService.createMenuItem(menuRequest())).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> menuService.updateMenuItem(8L, menuRequest())).isInstanceOf(ForbiddenException.class);
        }
        verifyNoInteractions(categories, menus, cache);
    }
    @Test void anonymousWritesAreRejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> categoryService.createCategory(categoryRequest())).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> menuService.createMenuItem(menuRequest())).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(categories, menus);
    }
    @Test void missingCategoryPreventsMenuSaveAndCacheInvalidation() {
        when(categories.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> menuService.createMenuItem(menuRequest())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(menus, cache);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
    @Test void rollbackDoesNotInvalidateCache() {
        when(categories.save(any())).thenAnswer(call -> call.getArgument(0));
        categoryService.createCategory(categoryRequest());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(cache);
    }
    @Test void invalidOrMissingUpdateIdFailsBeforeWriting() {
        assertThatThrownBy(() -> categoryService.updateCategory(0L, categoryRequest())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> menuService.updateMenuItem(-1L, menuRequest())).isInstanceOf(ValidationException.class);
        when(menus.findByIdForUpdate(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> menuService.updateMenuItem(8L, menuRequest())).isInstanceOf(ResourceNotFoundException.class);
        verify(menus, never()).save(any());
    }
}
