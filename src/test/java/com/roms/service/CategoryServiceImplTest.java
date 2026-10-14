package com.roms.service;

import com.roms.entity.Category;
import com.roms.enums.UserRole;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.exception.ValidationException;
import com.roms.mapper.CategoryMapper;
import com.roms.repository.CategoryRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {
    @Mock
    private CategoryRepository repository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new CategoryServiceImpl(repository, new CategoryMapper(), mock(com.roms.service.impl.MenuContextInvalidator.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserRole role) {
        UserPrincipal principal = UserPrincipal.builder().id(1L).username("reader").role(role).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Category category(boolean active) {
        return Category.builder().id(7L).name("Main dishes").description("Fresh food")
                .displayOrder(2).isActive(active).build();
    }

    @Test
    void tabletListFiltersInDatabaseBeforePaginationAndMapsMetadata() {
        authenticate(UserRole.TABLE_ACCOUNT);
        var pageable = PageRequest.of(1, 10, Sort.by("displayOrder").and(Sort.by("id")));
        when(repository.findByIsActiveTrue(pageable)).thenReturn(new PageImpl<>(List.of(category(true)), pageable, 11));

        var result = service.getCategories(PageRequest.of(1, 10));

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(11);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).singleElement().satisfies(dto -> {
            assertThat(dto.getId()).isEqualTo(7L);
            assertThat(dto.getName()).isEqualTo("Main dishes");
            assertThat(dto.getDescription()).isEqualTo("Fresh food");
            assertThat(dto.getDisplayOrder()).isEqualTo(2);
            assertThat(dto.getIsActive()).isTrue();
        });
        verify(repository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void everyStaffRoleCanReadInactiveCategories() {
        when(repository.findById(7L)).thenReturn(Optional.of(category(false)));
        for (UserRole role : List.of(UserRole.OWNER, UserRole.SERVICE_STAFF, UserRole.KITCHEN_STAFF, UserRole.INVENTORY_STAFF)) {
            authenticate(role);
            assertThat(service.getCategory(7L).getIsActive()).isFalse();
        }
    }

    @Test
    void staffListIncludesInactiveCategoriesAndCapsPageSizeWithStableSorting() {
        authenticate(UserRole.OWNER);
        var normalized = PageRequest.of(0, 100, Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id")));
        when(repository.findAll(normalized)).thenReturn(new PageImpl<>(List.of(category(false)), normalized, 1));

        var result = service.getCategories(PageRequest.of(0, 500, Sort.by(Sort.Order.desc("name"))));

        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getContent()).singleElement().satisfies(dto -> assertThat(dto.getIsActive()).isFalse());
        verify(repository, never()).findByIsActiveTrue(any());
    }

    @Test
    void tabletCanReadActiveCategoryButInactiveAndMissingHaveSameNotFoundResponse() {
        authenticate(UserRole.TABLE_ACCOUNT);
        when(repository.findById(7L)).thenReturn(Optional.of(category(true)));
        assertThat(service.getCategory(7L).getId()).isEqualTo(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(category(false)));
        assertThatThrownBy(() -> service.getCategory(7L)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Category not found");
        when(repository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCategory(8L)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Category not found");
    }

    @Test
    void unauthenticatedReadsNeverAccessRepository() {
        assertThatThrownBy(() -> service.getCategories(PageRequest.of(0, 20))).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.getCategory(7L)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void unsupportedSortAndNonpositiveIdFailBeforeQuerying() {
        authenticate(UserRole.OWNER);
        assertThatThrownBy(() -> service.getCategories(PageRequest.of(0, 20, Sort.by("menuItems.price"))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.getCategory(0L)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(repository);
    }
}
