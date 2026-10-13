package com.roms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtProperties jwtProperties;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        jwtProperties.setAccessTokenExpirationSeconds(900);

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("Should generate and validate JWT token with sub, role, and sid claims")
    void shouldGenerateAndValidateToken() {
        Long userId = 10L;
        String role = "SERVICE_STAFF";
        Long sessionId = 100L;

        String token = jwtTokenProvider.generateAccessToken(userId, role, sessionId);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo(role);
        assertThat(jwtTokenProvider.getSessionId(token)).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("Should reject tampered or invalid JWT token")
    void shouldRejectInvalidToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "OWNER", 5L);
        String tamperedToken = token + "xyz";

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
        assertThat(jwtTokenProvider.validateToken("invalid.token.structure")).isFalse();
    }

    @Test
    @DisplayName("Should fail when secret key is less than 256 bits (32 bytes)")
    void shouldFailWhenSecretTooShort() {
        JwtProperties shortSecretProps = new JwtProperties();
        shortSecretProps.setSecret("short-secret-under-32-chars");

        JwtTokenProvider provider = new JwtTokenProvider(shortSecretProps);
        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits");
    }

    @Test
    @DisplayName("Should generate high-entropy random token and compute SHA-256 hash")
    void shouldGenerateAndHashOpaqueToken() {
        String rawToken1 = TokenHashUtil.generateRandomToken();
        String rawToken2 = TokenHashUtil.generateRandomToken();

        assertThat(rawToken1).isNotBlank();
        assertThat(rawToken2).isNotBlank();
        assertThat(rawToken1).isNotEqualTo(rawToken2);

        String hash1 = TokenHashUtil.sha256(rawToken1);
        assertThat(hash1).hasSize(64); // SHA-256 hex string is 64 characters

        assertThat(TokenHashUtil.matches(rawToken1, hash1)).isTrue();
        assertThat(TokenHashUtil.matches(rawToken2, hash1)).isFalse();
    }
}
