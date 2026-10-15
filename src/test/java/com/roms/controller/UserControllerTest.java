package com.roms.controller;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class UserControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        String loginJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("GET /api/users: 401 Unauthorized when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/users: 403 Forbidden when logged in as SERVICE_STAFF")
    void shouldReturn403WhenNonOwnerRequestsUsers() throws Exception {
        String staffToken = loginAndGetAccessToken("service01", "Password@123");

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users: 200 OK when logged in as OWNER")
    void shouldReturn200WhenOwnerRequestsUsers() throws Exception {
        String ownerToken = loginAndGetAccessToken("owner", "Password@123");

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("POST /api/users: 201 Created when OWNER creates a new user")
    void shouldCreateUserSuccessfully() throws Exception {
        String ownerToken = loginAndGetAccessToken("owner", "Password@123");
        String createJson = """
                {
                    "username": "kitchen_new",
                    "password": "Password@123",
                    "fullName": "Đầu bếp mới",
                    "email": "kitchen_new@roms.vn",
                    "role": "KITCHEN_STAFF"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("kitchen_new"))
                .andExpect(jsonPath("$.data.role").value("KITCHEN_STAFF"));
    }

    @Test
    @DisplayName("GET /api/users/me/profile: 200 OK returns authenticated user's profile")
    void shouldReturnSelfProfile() throws Exception {
        String staffToken = loginAndGetAccessToken("service01", "Password@123");

        mockMvc.perform(get("/api/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("service01"))
                .andExpect(jsonPath("$.data.fullName").value("Trần Thị Thu Ngân"));
    }
}
