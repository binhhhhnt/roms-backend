package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AuthResult {

    private final AuthResponse authResponse;
    private final String rawRefreshToken;
    private final int maxAgeSeconds;
}
