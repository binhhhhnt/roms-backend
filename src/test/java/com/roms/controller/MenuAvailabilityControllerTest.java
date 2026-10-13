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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MenuAvailabilityControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
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
    @DisplayName("OWNER can update menu item status to TEMPORARILY_UNAVAILABLE")
    void ownerCanUpdateStatusToTemporarilyUnavailable() throws Exception {
        String token = loginAndGetToken("owner", "Password@123");

        mockMvc.perform(patch("/api/menu-items/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TEMPORARILY_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("OWNER can update menu item status to DISCONTINUED")
    void ownerCanUpdateStatusToDiscontinued() throws Exception {
        String token = loginAndGetToken("owner", "Password@123");

        mockMvc.perform(patch("/api/menu-items/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISCONTINUED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISCONTINUED"));
    }

    @Test
    @DisplayName("KITCHEN_STAFF can update menu item status to TEMPORARILY_UNAVAILABLE")
    void kitchenStaffCanUpdateStatusToTemporarilyUnavailable() throws Exception {
        String token = loginAndGetToken("kitchen01", "Password@123");

        mockMvc.perform(patch("/api/menu-items/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TEMPORARILY_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("KITCHEN_STAFF cannot mark menu item as DISCONTINUED (expect 403 Forbidden)")
    void kitchenStaffCannotMarkDiscontinued() throws Exception {
        String token = loginAndGetToken("kitchen01", "Password@123");

        mockMvc.perform(patch("/api/menu-items/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISCONTINUED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("SERVICE_STAFF cannot update menu status (expect 403 Forbidden)")
    void serviceStaffCannotUpdateMenuStatus() throws Exception {
        String token = loginAndGetToken("service01", "Password@123");

        mockMvc.perform(patch("/api/menu-items/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Unauthenticated request to update menu status returns 401 Unauthorized")
    void unauthenticatedCannotUpdateStatus() throws Exception {
        mockMvc.perform(patch("/api/menu-items/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Any authenticated user (e.g. TABLE_ACCOUNT) can check live sellability")
    void tableAccountCanCheckSellability() throws Exception {
        String token = loginAndGetToken("table_t01", "Password@123");

        mockMvc.perform(get("/api/menu-items/1/sellability?quantity=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menuItemId").value(1))
                .andExpect(jsonPath("$.data.sellable").value(true))
                .andExpect(jsonPath("$.data.requestedQuantity").value(2));
    }

    @Test
    @DisplayName("Unauthenticated request to check sellability returns 401 Unauthorized")
    void unauthenticatedCannotCheckSellability() throws Exception {
        mockMvc.perform(get("/api/menu-items/1/sellability"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Invalid quantity (<= 0) returns 400 Bad Request")
    void invalidQuantityReturnsBadRequest() throws Exception {
        String token = loginAndGetToken("table_t01", "Password@123");

        mockMvc.perform(get("/api/menu-items/1/sellability?quantity=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
