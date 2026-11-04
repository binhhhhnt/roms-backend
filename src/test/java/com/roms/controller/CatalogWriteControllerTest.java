package com.roms.controller;

import com.roms.dto.response.*;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogWriteControllerTest {
    private CategoryService categories;
    private MenuItemService menus;
    private MenuItemExcelService menuExcel;
    private MenuItemImageService images;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        categories = mock(CategoryService.class);
        menus = mock(MenuItemService.class);
        menuExcel = mock(MenuItemExcelService.class);
        images = mock(MenuItemImageService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(categories), new MenuItemController(menus, menuExcel), new MenuItemImageController(images))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void createsCategoryWithLocationAndEnvelope() throws Exception {
        when(categories.createCategory(any())).thenReturn(CategoryResponse.builder().id(3L).name("Main").isActive(true).build());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Main\",\"description\":null,\"displayOrder\":1,\"isActive\":true}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/categories/3"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.id").value(3));
    }

    @Test void categoryRequiredFieldsAndLengthsAreValidated() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.name").exists())
                .andExpect(jsonPath("$.error.details.isActive").exists());
        mvc.perform(put("/api/categories/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "a".repeat(101) + "\",\"displayOrder\":1,\"isActive\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(categories);
    }

    @Test void createsAndUpdatesMenuWithApprovedStatusCodes() throws Exception {
        when(menus.createMenuItem(any())).thenReturn(MenuItemResponse.builder().id(8L).build());
        when(menus.updateMenuItem(eq(8L), any())).thenReturn(MenuItemResponse.builder().id(8L).build());
        String body = "{\"categoryId\":3,\"name\":\"Soup\",\"price\":50000.00,\"isFeatured\":true}";
        mvc.perform(post("/api/menu-items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/menu-items/8"));
        mvc.perform(put("/api/menu-items/8").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(8));
    }

    @Test void menuRejectsNegativePriceExcessPrecisionAndMissingFields() throws Exception {
        mvc.perform(post("/api/menu-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":null,\"name\":\"\",\"price\":-1.00,\"isFeatured\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.categoryId").exists())
                .andExpect(jsonPath("$.error.details.name").exists())
                .andExpect(jsonPath("$.error.details.price").exists())
                .andExpect(jsonPath("$.error.details.isFeatured").exists());
        mvc.perform(post("/api/menu-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":3,\"name\":\"Soup\",\"price\":50.123,\"isFeatured\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.price").exists());
        verifyNoInteractions(menus);
    }

    @Test void multipartUploadAcceptsAllowedImageTypesAndRejectsInvalidMime() throws Exception {
        MockMultipartFile good = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        when(images.uploadImage(eq(9L), any(), anyInt())).thenReturn(MenuItemImageResponse.builder().id(12L).imageUrl("http://img").displayOrder(0).build());
        mvc.perform(multipart("/api/menu-items/9/images").file(good))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(12));
    }

    @Test void deleteImageDelegatesToService() throws Exception {
        mvc.perform(delete("/api/menu-items/9/images/2"))
                .andExpect(status().isNoContent());
        verify(images).deleteImage(9L, 2L);
    }
}
