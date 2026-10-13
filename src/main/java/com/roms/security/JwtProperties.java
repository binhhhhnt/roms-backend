package com.roms.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenExpirationSeconds = 900; // 15 minutes
    private int refreshTokenTabletExpirationDays = 90; // 90 days
    private int refreshTokenStaffExpirationDays = 7; // 7 days
    private String cookieName = "refreshToken";
    private String cookiePath = "/api/auth";
    private boolean cookieSecure = false;
    private String cookieSameSite = "Lax";
}
