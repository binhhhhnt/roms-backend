package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResult {

    public AuthResult(AuthResponse authResponse, String rawRefreshToken, int maxAgeSeconds) {
        this.authResponse = authResponse;
        this.rawRefreshToken = rawRefreshToken;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    private final AuthResponse authResponse;
    private final String rawRefreshToken;
    private final int maxAgeSeconds;
}
