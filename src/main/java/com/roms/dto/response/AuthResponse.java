package com.roms.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuthResponse {

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String tokenType, long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    private String accessToken;
    private String tokenType;
    private long expiresIn;
}
