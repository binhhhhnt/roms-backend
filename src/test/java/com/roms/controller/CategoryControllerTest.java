package com.roms.controller;

import com.roms.entity.Category;
import com.roms.enums.UserRole;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.mapper.CategoryMapper;
import com.roms.repository.CategoryRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoryControllerTest {
    private CategoryRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        repository = mock(CategoryRepository.class);
        var service = new CategoryServiceImpl(repository, new CategoryMapper(), mock(com.roms.service.impl.MenuContextInvalidator.class));
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserRole role) {
        var principal = UserPrincipal.builder().id(1L).username("reader").role(role).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void listReturnsApprovedEnvelopeAndDefaultPagination() throws Exception {
        authenticate(UserRole.TABLE_ACCOUNT);
        var pageable = PageRequest.of(0, 20, Sort.by("displayOrder").and(Sort.by("id")));
        var category = Category.builder().id(7L).name("Main dishes").displayOrder(2).isActive(true).build();
        when(repository.findByIsActiveTrue(pageable)).thenReturn(new PageImpl<>(List.of(category), pageable, 1));

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(7))
                .andExpect(jsonPath("$.data.content[0].isActive").value(true))
                .andExpect(jsonPath("$.data.content[0].createdAt").doesNotExist());
    }

    @Test
    void explicitPageSizeAndSortReachRepository() throws Exception {
        authenticate(UserRole.OWNER);
        var pageable = PageRequest.of(2, 5, Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id")));
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        mvc.perform(get("/api/categories").param("page", "2").param("size", "5").param("sort", "name,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2));
        verify(repository).findAll(pageable);
    }

    @Test
    void staffDetailReturnsInactiveCategory() throws Exception {
        authenticate(UserRole.SERVICE_STAFF);
        when(repository.findById(7L)).thenReturn(Optional.of(Category.builder().id(7L).name("Hidden")
                .description("Description").displayOrder(1).isActive(false).build()));
        mvc.perform(get("/api/categories/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hidden"))
                .andExpect(jsonPath("$.data.description").value("Description"))
                .andExpect(jsonPath("$.data.displayOrder").value(1))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void tabletInactiveDetailReturns404Envelope() throws Exception {
        authenticate(UserRole.TABLE_ACCOUNT);
        when(repository.findById(7L)).thenReturn(Optional.of(Category.builder().id(7L).isActive(false).build()));
        mvc.perform(get("/api/categories/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missingAuthenticationReturns401ForBothEndpoints() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/categories/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    void invalidIdAndUnsupportedSortReturn400Envelope() throws Exception {
        authenticate(UserRole.OWNER);
        mvc.perform(get("/api/categories/not-a-number")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAMETER"));
        mvc.perform(get("/api/categories/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/categories").param("sort", "unknown,asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(repository);
    }
}
