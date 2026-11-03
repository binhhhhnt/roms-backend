package com.roms.service;

import com.roms.controller.CatalogDeletionController;
import com.roms.entity.Category;
import com.roms.entity.MenuItem;
import com.roms.entity.MenuItemImage;
import com.roms.enums.UserRole;
import com.roms.exception.*;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.CatalogDeletionServiceImpl;
import com.roms.service.impl.MenuContextInvalidator;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogDeletionServiceTest {
    private CategoryRepository categories;
    private MenuItemRepository menus;
    private OrderItemRepository orders;
    private RecipeItemRepository recipes;
    private MenuItemImageRepository images;
    private MenuItemImageService imageService;
    private MenuContextInvalidator invalidator;
    private CatalogDeletionService service;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        categories = mock(CategoryRepository.class);
        menus = mock(MenuItemRepository.class);
        orders = mock(OrderItemRepository.class);
        recipes = mock(RecipeItemRepository.class);
        images = mock(MenuItemImageRepository.class);
        imageService = mock(MenuItemImageService.class);
        invalidator = mock(MenuContextInvalidator.class);
        service = new CatalogDeletionServiceImpl(categories, menus, orders, recipes, images, imageService, invalidator);
        mvc = MockMvcBuilders.standaloneSetup(new CatalogDeletionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate(UserRole.OWNER);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authenticate(UserRole role) {
        var user = UserPrincipal.builder().id(1L).username("owner").role(role).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void existingCategory() {
        when(categories.findAllForUpdateInDisplayOrder()).thenReturn(List.of(Category.builder().id(3L).build()));
    }

    private void existingMenu() {
        when(menus.findByIdForUpdate(7L)).thenReturn(Optional.of(MenuItem.builder().id(7L).build()));
    }

    @Test void deletesEmptyCategoryAndReturnsNoContent() throws Exception {
        existingCategory();
        mvc.perform(delete("/api/categories/3")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(categories).delete(any(Category.class));
        verify(categories).flush();
        verify(invalidator).afterCommit();
    }

    @Test void categoryWithDishesReturnsConflictAndKeepsData() throws Exception {
        existingCategory();
        when(menus.existsByCategoryId(3L)).thenReturn(true);
        mvc.perform(delete("/api/categories/3")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_IN_USE"));
        verify(categories, never()).delete(any());
        verify(categories, never()).saveAll(any());
        verifyNoInteractions(invalidator);
    }

    @Test void deletingMiddleCategoryCompactsOrderAndKeepsIdsAndInactiveCategories() {
        var first = Category.builder().id(10L).displayOrder(1).build();
        var removed = Category.builder().id(20L).displayOrder(2).build();
        var paused = Category.builder().id(30L).displayOrder(3).isActive(false).build();
        var last = Category.builder().id(40L).displayOrder(4).build();
        when(categories.findAllForUpdateInDisplayOrder()).thenReturn(List.of(first, removed, paused, last));
        service.deleteCategory(20L);
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(paused.getDisplayOrder()).isEqualTo(2);
        assertThat(last.getDisplayOrder()).isEqualTo(3);
        assertThat(paused.getId()).isEqualTo(30L);
        assertThat(paused.getIsActive()).isFalse();
        verify(categories).delete(removed);
        verify(categories).saveAll(List.of(paused, last));
        verify(invalidator).afterCommit();
    }

    @Test void deletingFirstCategoryShiftsEveryRemainingPosition() {
        var first = Category.builder().id(10L).displayOrder(1).build();
        var second = Category.builder().id(20L).displayOrder(2).build();
        var third = Category.builder().id(30L).displayOrder(3).build();
        when(categories.findAllForUpdateInDisplayOrder()).thenReturn(List.of(first, second, third));
        service.deleteCategory(10L);
        assertThat(second.getDisplayOrder()).isEqualTo(1);
        assertThat(third.getDisplayOrder()).isEqualTo(2);
        verify(categories).saveAll(List.of(second, third));
    }

    @Test void deletingLastCategoryDoesNotRewriteUnchangedPositions() {
        var first = Category.builder().id(10L).displayOrder(1).build();
        var last = Category.builder().id(20L).displayOrder(2).build();
        when(categories.findAllForUpdateInDisplayOrder()).thenReturn(List.of(first, last));
        service.deleteCategory(20L);
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        verify(categories, never()).saveAll(any());
    }

    @Test void deletionRepairsExistingGapsAndTiesWhilePreservingDisplayOrder() {
        var first = Category.builder().id(10L).displayOrder(0).build();
        var tied = Category.builder().id(20L).displayOrder(0).build();
        var removed = Category.builder().id(30L).displayOrder(5).build();
        var last = Category.builder().id(40L).displayOrder(50).build();
        when(categories.findAllForUpdateInDisplayOrder()).thenReturn(List.of(first, tied, removed, last));
        service.deleteCategory(30L);
        assertThat(List.of(first, tied, last)).extracting(Category::getDisplayOrder).containsExactly(1, 2, 3);
        verify(categories).saveAll(List.of(first, tied, last));
    }

    @Test void failedDeletionDoesNotChangeOtherCategoryPositions() {
        var removed = Category.builder().id(10L).displayOrder(1).build();
        var last = Category.builder().id(20L).displayOrder(2).build();
        when(categories.findAllForUpdateInDisplayOrder()).thenReturn(List.of(removed, last));
        doThrow(new DataIntegrityViolationException("foreign key")).when(categories).flush();
        assertThatThrownBy(() -> service.deleteCategory(10L)).isInstanceOf(BusinessRuleException.class);
        assertThat(last.getDisplayOrder()).isEqualTo(2);
        verify(categories, never()).saveAll(any());
        verifyNoInteractions(invalidator);
    }

    @Test void deletesUnusedDishWithItsRecipeAndImages() throws Exception {
        existingMenu();
        when(images.findCatalogImages(List.of(7L))).thenReturn(List.of(
                MenuItemImage.builder().id(10L).build(), MenuItemImage.builder().id(11L).build()));
        mvc.perform(delete("/api/menu-items/7")).andExpect(status().isNoContent()).andExpect(content().string(""));
        var order = inOrder(imageService, recipes, menus, invalidator);
        order.verify(imageService).deleteImage(7L, 10L);
        order.verify(imageService).deleteImage(7L, 11L);
        order.verify(recipes).deleteByMenuItemId(7L);
        order.verify(recipes).flush();
        order.verify(menus).delete(any(MenuItem.class));
        order.verify(menus).flush();
        order.verify(invalidator).afterCommit();
    }

    @Test void orderedDishReturnsConflictBeforeDeletingImagesOrRecipe() throws Exception {
        existingMenu();
        when(orders.existsByMenuItemId(7L)).thenReturn(true);
        mvc.perform(delete("/api/menu-items/7")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MENU_ITEM_IN_USE"));
        verify(menus, never()).delete(any());
        verifyNoInteractions(recipes, images, imageService, invalidator);
    }

    @Test void imageFailureDoesNotDeleteDishOrRecipe() {
        existingMenu();
        when(images.findCatalogImages(List.of(7L))).thenReturn(List.of(MenuItemImage.builder().id(10L).build()));
        doThrow(new BusinessRuleException("IMAGE_DELETE_FAILED", "Retry image cleanup")).when(imageService).deleteImage(7L, 10L);
        assertThatThrownBy(() -> service.deleteMenuItem(7L)).isInstanceOf(BusinessRuleException.class);
        verify(menus, never()).delete(any());
        verifyNoInteractions(recipes, invalidator);
    }

    @Test void concurrentReferenceConflictIsTranslatedAndDoesNotInvalidate() throws Exception {
        existingCategory();
        doThrow(new DataIntegrityViolationException("foreign key")).when(categories).flush();
        mvc.perform(delete("/api/categories/3")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_IN_USE"));
        verifyNoInteractions(invalidator);
    }

    @Test void missingResourcesReturn404() throws Exception {
        mvc.perform(delete("/api/categories/3")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/menu-items/7")).andExpect(status().isNotFound());
        verifyNoInteractions(images, orders, recipes, imageService, invalidator);
    }

    @Test void invalidIdentifiersReturn400WithoutPersistenceAccess() throws Exception {
        for (var path : List.of("/api/categories/0", "/api/categories/no", "/api/menu-items/-1", "/api/menu-items/no")) {
            mvc.perform(delete(path)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(categories, menus, images, orders, recipes, imageService, invalidator);
    }

    @Test void everyNonOwnerRoleIsForbiddenBeforeResourceLookup() throws Exception {
        for (var role : UserRole.values()) {
            if (role == UserRole.OWNER) continue;
            authenticate(role);
            mvc.perform(delete("/api/categories/3")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/menu-items/7")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(categories, menus, images, orders, recipes, imageService, invalidator);
    }

    @Test void anonymousDeletionIsRejectedBeforeResourceLookup() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(delete("/api/categories/3")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/menu-items/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(categories, menus, images, orders, recipes, imageService, invalidator);
    }
}
