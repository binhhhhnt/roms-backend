package com.roms.repository;

import com.roms.enums.MenuItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.Set;
import com.roms.entity.MenuItem;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in verification of existing schema/data only; no Flyway, DDL or fixture writes. */
@EnabledIfSystemProperty(named = "roms.test.postgres", matches = "true")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false", "spring.datasource.hikari.connection-timeout=5000"})
@Transactional(readOnly = true)
class MenuCatalogPostgresReadTest {
    @Autowired MenuItemRepository menus;
    @Autowired MenuItemImageRepository images;

    @Test void catalogQueriesRespectVisibilityCategoryFilterAndImageOrder() {
        var pageable = PageRequest.of(0, 20, Sort.by("name").and(Sort.by("id")));
        var tabletPage = menus.findCatalog(null, EnumSet.of(MenuItemStatus.AVAILABLE), true, pageable);
        assertThat(tabletPage.getContent()).allSatisfy(menu -> {
            assertThat(menu.getCategory().getIsActive()).isTrue();
            assertThat(menu.getStatus()).isEqualTo(MenuItemStatus.AVAILABLE);
        });
        var allPage = menus.findCatalog(null, EnumSet.allOf(MenuItemStatus.class), false, pageable);
        assertThat(allPage.getTotalElements()).isGreaterThanOrEqualTo(tabletPage.getTotalElements());
        if (allPage.hasContent()) {
            var first = allPage.getContent().getFirst();
            var categoryPage = menus.findCatalog(first.getCategory().getId(), EnumSet.allOf(MenuItemStatus.class), false, pageable);
            assertThat(categoryPage.getContent()).allSatisfy(menu -> assertThat(menu.getCategory().getId()).isEqualTo(first.getCategory().getId()));
            assertThat(menus.findCatalogById(first.getId())).isPresent();
            var imageRows = images.findCatalogImages(List.of(first.getId()));
            assertThat(imageRows).allSatisfy(image -> assertThat(image.getMenuItem().getId()).isEqualTo(first.getId()));
            assertThat(imageRows).isSortedAccordingTo(java.util.Comparator
                    .comparing(com.roms.entity.MenuItemImage::getDisplayOrder).thenComparing(com.roms.entity.MenuItemImage::getId));
        }
    }

    @Test void databaseFiltersStatusCategoryAndRoleBeforeCountingAndPaging() {
        var allRows = menus.findAll();
        assertThat(allRows).as("Existing catalog data is required for the read-only database check").isNotEmpty();
        List<Long> categoryIds = new ArrayList<>();
        categoryIds.add(null);
        categoryIds.addAll(allRows.stream().map(row -> row.getCategory().getId()).distinct().toList());
        List<Set<MenuItemStatus>> scopes = List.of(
                EnumSet.allOf(MenuItemStatus.class),
                EnumSet.of(MenuItemStatus.AVAILABLE, MenuItemStatus.TEMPORARILY_UNAVAILABLE),
                EnumSet.of(MenuItemStatus.AVAILABLE));
        for (int scope = 0; scope < scopes.size(); scope++) {
            boolean tablet = scope == 2;
            List<Set<MenuItemStatus>> filters = new ArrayList<>();
            filters.add(scopes.get(scope));
            for (MenuItemStatus status : scopes.get(scope)) filters.add(EnumSet.of(status));
            for (Set<MenuItemStatus> statuses : filters) {
                for (Long categoryId : categoryIds) {
                    var expected = allRows.stream()
                            .filter(row -> categoryId == null || categoryId.equals(row.getCategory().getId()))
                            .filter(row -> statuses.contains(row.getStatus()))
                            .filter(row -> !tablet || Boolean.TRUE.equals(row.getCategory().getIsActive()))
                            .sorted(Comparator.comparing(MenuItem::getId)).map(MenuItem::getId).toList();
                    List<Long> actual = new ArrayList<>();
                    int totalPages = (expected.size() + 1) / 2;
                    // Include one page beyond the end to exercise the count query as well.
                    for (int page = 0; page <= totalPages; page++) {
                        var result = menus.findCatalog(categoryId, statuses, tablet, PageRequest.of(page, 2, Sort.by("id")));
                        assertThat(result.getTotalElements()).isEqualTo(expected.size());
                        assertThat(result.getTotalPages()).isEqualTo(totalPages);
                        actual.addAll(result.getContent().stream().map(MenuItem::getId).toList());
                    }
                    assertThat(actual).containsExactlyElementsOf(expected);
                }
            }
        }
    }
}
