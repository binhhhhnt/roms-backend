package com.roms.security;

import org.junit.jupiter.api.*;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class TokenHashUtilTest {
    @Test @DisplayName("Token hash: SHA-256 matches the published abc test vector")
    void knownSha256Vector() {
        assertThat(TokenHashUtil.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test @DisplayName("Token hash: empty token has the SHA-256 empty-string digest")
    void emptyStringBoundary() {
        assertThat(TokenHashUtil.sha256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test @DisplayName("Token hash: null input is rejected")
    void nullTokenRejected() {
        assertThatThrownBy(() -> TokenHashUtil.sha256(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token cannot be null");
    }

    @Test @DisplayName("Token hash: matching token is accepted; wrong or null values are rejected")
    void matchesAndRejects() {
        var hash = TokenHashUtil.sha256("otp-123456");
        assertThat(TokenHashUtil.matches("otp-123456", hash)).isTrue();
        assertThat(TokenHashUtil.matches("otp-654321", hash)).isFalse();
        assertThat(TokenHashUtil.matches(null, hash)).isFalse();
        assertThat(TokenHashUtil.matches("otp-123456", null)).isFalse();
    }

    @Test @DisplayName("Token generation: output is URL-safe unpadded Base64 of 32 bytes")
    void randomTokenEncoding() {
        String token = TokenHashUtil.generateRandomToken();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }
}
