package com.roms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ExcelEndpointsControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private com.roms.repository.CategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        for (String catName : java.util.List.of("Món chính", "Đồ uống")) {
            if (categoryRepository.findByName(catName).isEmpty()) {
                categoryRepository.save(com.roms.entity.Category.builder()
                        .name(catName)
                        .displayOrder(1)
                        .isActive(true)
                        .build());
            }
        }
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String loginJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("OWNER can download menu items excel template and export")
    void ownerCanDownloadMenuTemplateAndExport() throws Exception {
        String token = loginAndGetToken("owner", "Password@123");

        mockMvc.perform(get("/api/menu-items/excel/template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=menu_items_template.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/api/menu-items/excel/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=menu_items_export.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("SERVICE_STAFF cannot download menu items excel template (403 Forbidden)")
    void serviceStaffCannotDownloadMenuTemplate() throws Exception {
        String token = loginAndGetToken("service01", "Password@123");

        mockMvc.perform(get("/api/menu-items/excel/template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OWNER can download ingredients excel template and export")
    void ownerCanDownloadIngredientsTemplateAndExport() throws Exception {
        String token = loginAndGetToken("owner", "Password@123");

        mockMvc.perform(get("/api/ingredients/excel/template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ingredients_template.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/api/ingredients/excel/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ingredients_export.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("OWNER can import menu items from generated template")
    void ownerCanImportMenuItemsFromTemplate() throws Exception {
        String token = loginAndGetToken("owner", "Password@123");

        // 1. Download template bytes
        MvcResult templateResult = mockMvc.perform(get("/api/menu-items/excel/template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        byte[] templateBytes = templateResult.getResponse().getContentAsByteArray();

        MockMultipartFile file = new MockMultipartFile(
                "file", "menu_items_template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        // 2. Upload template (has 2 example items)
        mockMvc.perform(multipart("/api/menu-items/excel/import")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.errorCount").value(0));
    }

    @Test
    @DisplayName("OWNER can import ingredients from generated template")
    void ownerCanImportIngredientsFromTemplate() throws Exception {
        String token = loginAndGetToken("owner", "Password@123");

        // 1. Download template bytes
        MvcResult templateResult = mockMvc.perform(get("/api/ingredients/excel/template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        byte[] templateBytes = templateResult.getResponse().getContentAsByteArray();

        MockMultipartFile file = new MockMultipartFile(
                "file", "ingredients_template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        // 2. Upload template (has 2 example ingredients)
        mockMvc.perform(multipart("/api/ingredients/excel/import")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.errorCount").value(0));
    }
}

