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
    private MenuItemImageService images;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        categories = mock(CategoryService.class); menus = mock(MenuItemService.class); images = mock(MenuItemImageService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(categories), new MenuItemController(menus), new MenuItemImageController(images))
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
        for (String price : new String[]{"-1", "0.001", "10000000000"}) {
            mvc.perform(post("/api/menu-items").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"categoryId\":3,\"name\":\"Soup\",\"price\":" + price + ",\"isFeatured\":false}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.price").exists());
        }
        mvc.perform(post("/api/menu-items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(menus);
    }
    @Test void imageUploadAndDeleteUseMetadataEnvelopeAndEmpty204() throws Exception {
        when(images.uploadImage(eq(8L), any(), eq(2))).thenReturn(MenuItemImageResponse.builder().id(12L).menuItemId(8L)
                .imageUrl("https://example.com/image.png").displayOrder(2).build());
        mvc.perform(multipart("/api/menu-items/8/images").file(new MockMultipartFile("file", "food.png", "image/png", new byte[]{1}))
                .param("displayOrder", "2"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/menu-items/8/images/12"))
                .andExpect(jsonPath("$.data.displayOrder").value(2));
        mvc.perform(delete("/api/menu-items/8/images/12")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(images).deleteImage(8L, 12L);
    }
    @Test void missingImagePartReturns400() throws Exception {
        mvc.perform(multipart("/api/menu-items/8/images"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(images);
    }
}
