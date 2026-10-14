package com.roms.controller;

import com.roms.config.SecurityConfig;
import com.roms.dto.response.MenuItemSellabilityResponse;
import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.mapper.*;
import com.roms.repository.*;
import com.roms.security.*;
import com.roms.service.*;
import com.roms.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = MenuItemReadSecurityTest.TestConfig.class)
class MenuItemReadSecurityTest {
    @Configuration @EnableWebMvc
    @Import({SecurityConfig.class, MenuItemController.class, GlobalExceptionHandler.class})
    static class TestConfig implements WebMvcConfigurer {
        @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new PageableHandlerMethodArgumentResolver());
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean RefreshTokenRepository sessions() { return mock(RefreshTokenRepository.class); }
        @Bean MenuItemRepository menus() { return mock(MenuItemRepository.class); }
        @Bean MenuItemImageRepository images() { return mock(MenuItemImageRepository.class); }
        @Bean MenuAvailabilityService availability() { return mock(MenuAvailabilityService.class); }
        @Bean UserDetailsService userDetailsService() { return new InMemoryUserDetailsManager(); }
        @Bean JwtProperties jwtProperties() {
            var properties = new JwtProperties(); properties.setSecret("test-only-signing-key-at-least-32-bytes"); return properties;
        }
        @Bean JwtTokenProvider tokenProvider(JwtProperties properties) { return new JwtTokenProvider(properties); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenProvider provider, UserRepository users, RefreshTokenRepository sessions) {
            return new JwtAuthenticationFilter(provider, users, sessions);
        }
        @Bean MenuItemService menuService(MenuItemRepository menus, MenuItemImageRepository images, MenuAvailabilityService availability) {
            return new MenuItemServiceImpl(menus, mock(CategoryRepository.class), new MenuItemMapper(), mock(MenuContextInvalidator.class),
                    images, new MenuItemImageMapper(), new CategoryMapper(), availability);
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository sessions;
    @Autowired MenuItemRepository menus;
    @Autowired MenuItemImageRepository images;
    @Autowired MenuAvailabilityService availability;
    private MockMvc mvc;
    private User user;
    private MenuItem menu;
    private String token;
    @BeforeEach void setUp() {
        reset(users, sessions, menus, images, availability);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        user = User.builder().id(1L).username("tablet").role(UserRole.TABLE_ACCOUNT).isActive(true)
                .table(RestaurantTable.builder().id(3L).build()).build();
        menu = MenuItem.builder().id(8L).name("Soup").price(new BigDecimal("50000.00")).isFeatured(true).status(MenuItemStatus.AVAILABLE)
                .category(Category.builder().id(4L).name("Main").isActive(true).displayOrder(1).build()).build();
        token = tokens.generateAccessToken(1L, UserRole.TABLE_ACCOUNT, 2L);
    }
    private void activeSession() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(sessions.findByIdAndRevokedAtIsNull(2L)).thenReturn(Optional.of(RefreshToken.builder().id(2L).user(user)
                .expiresAt(LocalDateTime.now().plusDays(1)).build()));
    }
    private void detail() {
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(menu));
        when(images.findCatalogImages(List.of(8L))).thenReturn(List.of());
        when(availability.checkSellability(8L, 1)).thenReturn(MenuItemSellabilityResponse.builder().sellable(true).requestedQuantity(1).build());
    }
    @Test void validJwtReturnsDetailWithoutInventoryInternals() throws Exception {
        activeSession(); detail();
        mvc.perform(get("/api/menu-items/8").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category.id").value(4))
                .andExpect(jsonPath("$.data.images").isArray())
                .andExpect(jsonPath("$.data.availability.sellable").value(true))
                .andExpect(jsonPath("$.data.availability.insufficientIngredients").doesNotExist());
    }
    @Test void listBindsPagingCategoryQuantityAndSort() throws Exception {
        activeSession();
        var page = PageRequest.of(1, 5, Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")));
        when(menus.findCatalog(4L, true, page)).thenReturn(new PageImpl<>(List.of(menu), page, 6));
        when(images.findCatalogImages(List.of(8L))).thenReturn(List.of());
        when(availability.checkSellability(8L, 2)).thenReturn(MenuItemSellabilityResponse.builder().sellable(false).requestedQuantity(2).build());
        mvc.perform(get("/api/menu-items").header("Authorization", "Bearer " + token).param("categoryId", "4")
                .param("quantity", "2").param("page", "1").param("size", "5").param("sort", "price,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.content[0].availability.requestedQuantity").value(2));
    }
    @Test void missingOrInvalidJwtIsRejectedByActualSecurityChain() throws Exception {
        mvc.perform(get("/api/menu-items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/menu-items/8").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(menus, images, availability);
    }
    @Test void revokedSessionAndDisabledAccountCannotRead() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(sessions.findByIdAndRevokedAtIsNull(2L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/menu-items/8").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        user.setIsActive(false);
        mvc.perform(get("/api/menu-items").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(menus, images, availability);
    }
    @Test void hiddenItemReturns404AfterJwtAuthentication() throws Exception {
        activeSession(); menu.setStatus(MenuItemStatus.DISCONTINUED);
        when(menus.findCatalogById(8L)).thenReturn(Optional.of(menu));
        mvc.perform(get("/api/menu-items/8").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        verifyNoInteractions(images, availability);
    }
    @Test void badQuantityIdAndSortReturn400AfterAuthentication() throws Exception {
        activeSession();
        mvc.perform(get("/api/menu-items/8").header("Authorization", "Bearer " + token).param("quantity", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/menu-items/not-a-number").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/menu-items").header("Authorization", "Bearer " + token).param("sort", "invalid,asc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(menus, images, availability);
    }
}
