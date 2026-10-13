package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.LoginRequest;
import com.roms.dto.response.AuthResponse;
import com.roms.dto.response.AuthResult;
import com.roms.dto.response.UserProfileResponse;
import com.roms.exception.UnauthorizedException;
import com.roms.security.JwtProperties;
import com.roms.security.UserPrincipal;
import com.roms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String deviceId = request.getDeviceId() != null
                ? request.getDeviceId()
                : httpRequest.getHeader("User-Agent");

        AuthResult result = authService.login(request, deviceId);
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), result.getMaxAgeSeconds());

        return ResponseEntity.ok(ApiResponse.success("Login successful", result.getAuthResponse()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "${app.jwt.cookie-name:refreshToken}", required = false) String refreshTokenCookie,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (refreshTokenCookie == null || refreshTokenCookie.trim().isEmpty()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        String deviceId = httpRequest.getHeader("User-Agent");
        AuthResult result = authService.refresh(refreshTokenCookie, deviceId);
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), result.getMaxAgeSeconds());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", result.getAuthResponse()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse httpResponse) {

        authService.logout(principal);
        clearRefreshTokenCookie(httpResponse);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {

        UserProfileResponse profile = authService.getProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", profile));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String tokenValue, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), tokenValue)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path(jwtProperties.getCookiePath())
                .sameSite(jwtProperties.getCookieSameSite())
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path(jwtProperties.getCookiePath())
                .sameSite(jwtProperties.getCookieSameSite())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
