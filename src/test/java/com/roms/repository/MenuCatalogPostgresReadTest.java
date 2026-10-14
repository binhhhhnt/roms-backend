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
        var tabletPage = menus.findCatalog(null, true, pageable);
        assertThat(tabletPage.getContent()).allSatisfy(menu -> {
            assertThat(menu.getCategory().getIsActive()).isTrue();
            assertThat(menu.getStatus()).isNotEqualTo(MenuItemStatus.DISCONTINUED);
        });
        var allPage = menus.findCatalog(null, false, pageable);
        assertThat(allPage.getTotalElements()).isGreaterThanOrEqualTo(tabletPage.getTotalElements());
        if (allPage.hasContent()) {
            var first = allPage.getContent().getFirst();
            var categoryPage = menus.findCatalog(first.getCategory().getId(), false, pageable);
            assertThat(categoryPage.getContent()).allSatisfy(menu -> assertThat(menu.getCategory().getId()).isEqualTo(first.getCategory().getId()));
            assertThat(menus.findCatalogById(first.getId())).isPresent();
            var imageRows = images.findCatalogImages(List.of(first.getId()));
            assertThat(imageRows).allSatisfy(image -> assertThat(image.getMenuItem().getId()).isEqualTo(first.getId()));
            assertThat(imageRows).isSortedAccordingTo(java.util.Comparator
                    .comparing(com.roms.entity.MenuItemImage::getDisplayOrder).thenComparing(com.roms.entity.MenuItemImage::getId));
        }
    }
}
