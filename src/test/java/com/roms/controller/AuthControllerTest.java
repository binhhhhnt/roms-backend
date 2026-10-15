package com.roms.controller;

import com.roms.security.TokenHashUtil;
import com.roms.service.OtpRedisService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }


    @Autowired
    private OtpRedisService otpRedisService;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");
    private static final Pattern COOKIE_PATTERN = Pattern.compile("refreshToken=([^;]+);");

    @Test
    @DisplayName("Login success: returns 200, JWT access token, and HttpOnly refresh cookie")
    void shouldLoginSuccessfully() throws Exception {
        String loginJson = "{\"username\":\"owner\",\"password\":\"Password@123\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refreshToken=");
        assertThat(setCookieHeader).contains("Path=/api/auth");
        assertThat(setCookieHeader).contains("HttpOnly");
    }

    @Test
    @DisplayName("Login failure: wrong password returns 401 without revealing user existence")
    void shouldFailLoginWithWrongPassword() throws Exception {
        String loginJson = "{\"username\":\"owner\",\"password\":\"WrongPassword\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Login failure: non-existent username returns 401 without revealing user existence")
    void shouldFailLoginWithNonExistentUser() throws Exception {
        String loginJson = "{\"username\":\"non_existent_user\",\"password\":\"Password@123\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TABLE_ACCOUNT policy: new login automatically revokes previous session")
    void shouldRevokePreviousSessionOnTableAccountLogin() throws Exception {
        String loginJson = "{\"username\":\"table_t01\",\"password\":\"Password@123\"}";

        // 1st login
        MvcResult result1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();
        String cookie1 = extractRefreshTokenFromCookieHeader(result1.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        // 2nd login
        MvcResult result2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();
        String cookie2 = extractRefreshTokenFromCookieHeader(result2.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        assertThat(cookie1).isNotEqualTo(cookie2);

        // Trying to refresh using the 1st session token should fail because it was revoked
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", cookie1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session has been revoked"));

        // Refreshing with the 2nd session token should succeed
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", cookie2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Refresh token rotation: valid refresh token rotates token and returns new access token")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        String loginJson = "{\"username\":\"service01\",\"password\":\"Password@123\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String rawToken1 = extractRefreshTokenFromCookieHeader(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        // Refresh call
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String rawToken2 = extractRefreshTokenFromCookieHeader(refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(rawToken2).isNotNull();
        assertThat(rawToken2).isNotEqualTo(rawToken1);
    }

    @Test
    @DisplayName("Replay attack prevention: already rotated refresh token is rejected")
    void shouldRejectReplayAttackWithRotatedToken() throws Exception {
        String loginJson = "{\"username\":\"kitchen01\",\"password\":\"Password@123\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String rawToken1 = extractRefreshTokenFromCookieHeader(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        // 1st refresh succeeds
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken1)))
                .andExpect(status().isOk());

        // 2nd refresh with the SAME token1 must be rejected
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawToken1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout flow: returns 204, clears cookie, and revokes session so accessToken is immediately invalidated")
    void shouldLogoutAndRevokeSession() throws Exception {
        String loginJson = "{\"username\":\"inventory01\",\"password\":\"Password@123\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractAccessToken(loginResult.getResponse().getContentAsString());

        // Verify authenticated access before logout
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("inventory01"));

        // Logout
        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andReturn();

        String logoutCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(logoutCookie).contains("Max-Age=0");

        // Verify that access token is now rejected because session was revoked in PostgreSQL
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me: returns authenticated user profile")
    void shouldGetProfileForAuthenticatedUser() throws Exception {
        String loginJson = "{\"username\":\"table_t01\",\"password\":\"Password@123\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractAccessToken(loginResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("table_t01"))
                .andExpect(jsonPath("$.data.role").value("TABLE_ACCOUNT"))
                .andExpect(jsonPath("$.data.tableId").isNumber());
    }

    @Test
    @DisplayName("GET /api/auth/me without authorization header returns 401")
    void shouldRejectUnauthenticatedMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password: 200 OK sends OTP and stores hash in Redis")
    void shouldTriggerForgotPassword() throws Exception {
        String requestJson = "{\"email\":\"owner@roms.vn\"}";

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(otpRedisService.getOtpHash("owner@roms.vn")).isPresent();
    }

    @Test
    @DisplayName("POST /api/auth/reset-password: 200 OK resets password with valid OTP")
    void shouldResetPasswordWithValidOtp() throws Exception {
        String email = "owner@roms.vn";
        String rawOtp = "123456";
        otpRedisService.storeOtp(email, TokenHashUtil.sha256(rawOtp));

        String resetJson = String.format("""
                {
                    "email": "%s",
                    "otp": "%s",
                    "newPassword": "NewOwnerPassword@123"
                }
                """, email, rawOtp);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // OTP should be deleted from Redis after successful reset
        assertThat(otpRedisService.getOtpHash(email)).isEmpty();

        // Should be able to login with new password
        String newLoginJson = "{\"username\":\"owner\",\"password\":\"NewOwnerPassword@123\"}";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newLoginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private String extractAccessToken(String responseJson) {
        Matcher matcher = TOKEN_PATTERN.matcher(responseJson);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Failed to extract accessToken from: " + responseJson);
    }

    private String extractRefreshTokenFromCookieHeader(String setCookieHeader) {
        if (setCookieHeader == null) {
            return null;
        }
        Matcher matcher = COOKIE_PATTERN.matcher(setCookieHeader);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
