package com.roms.controller;

import com.roms.common.PageResponse;
import com.roms.service.MenuItemService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuItemDefaultSortTest {
    @Test void defaultHttpRequestUsesInsertionOrderAndKeepsExplicitNameSort() throws Exception {
        var service = mock(MenuItemService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new MenuItemController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        for (String property : List.of("id", "name")) {
            var pageable = PageRequest.of(0, 20, Sort.by(property));
            when(service.getMenuItems(null, null, 1, pageable)).thenReturn(PageResponse.from(new PageImpl<>(List.of()), value -> null));
        }
        mvc.perform(get("/api/menu-items")).andExpect(status().isOk());
        verify(service).getMenuItems(null, null, 1, PageRequest.of(0, 20, Sort.by("id")));
        mvc.perform(get("/api/menu-items").param("sort", "name,asc")).andExpect(status().isOk());
        verify(service).getMenuItems(null, null, 1, PageRequest.of(0, 20, Sort.by("name")));
    }
}
