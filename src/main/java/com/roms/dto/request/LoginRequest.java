package com.roms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginRequest {

    public LoginRequest() {
    }

    public LoginRequest(String username, String password, String deviceId) {
        this.username = username;
        this.password = password;
        this.deviceId = deviceId;
    }

    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    private String deviceId;
}
