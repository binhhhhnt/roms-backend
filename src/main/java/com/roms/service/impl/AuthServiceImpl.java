package com.roms.service.impl;

import com.roms.dto.request.LoginRequest;
import com.roms.dto.response.AuthResponse;
import com.roms.dto.response.AuthResult;
import com.roms.dto.response.UserProfileResponse;
import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.UserRepository;
import com.roms.security.JwtProperties;
import com.roms.security.JwtTokenProvider;
import com.roms.security.TokenHashUtil;
import com.roms.security.UserPrincipal;
import com.roms.service.AuthService;
import com.roms.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public AuthResult login(LoginRequest request, String deviceId) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account is inactive");
        }

        boolean isTableAccount = (user.getRole() == UserRole.TABLE_ACCOUNT);
        int expiryDays = isTableAccount
                ? jwtProperties.getRefreshTokenTabletExpirationDays()
                : jwtProperties.getRefreshTokenStaffExpirationDays();
        int maxAgeSeconds = expiryDays * 24 * 60 * 60;

        String rawRefreshToken = TokenHashUtil.generateRandomToken();
        RefreshToken session = refreshTokenService.createSession(user, rawRefreshToken, deviceId, expiryDays);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole(), session.getId());

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .build();

        return AuthResult.builder()
                .authResponse(authResponse)
                .rawRefreshToken(rawRefreshToken)
                .maxAgeSeconds(maxAgeSeconds)
                .build();
    }

    @Override
    @Transactional
    public AuthResult refresh(String rawRefreshToken, String deviceId) {
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        String hash = TokenHashUtil.sha256(rawRefreshToken.trim());
        RefreshToken session = refreshTokenService.findSessionByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (session.getRevokedAt() != null) {
            throw new UnauthorizedException("Session has been revoked");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Session has expired");
        }

        User user = session.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account is inactive");
        }

        boolean isTableAccount = (user.getRole() == UserRole.TABLE_ACCOUNT);
        int expiryDays = isTableAccount
                ? jwtProperties.getRefreshTokenTabletExpirationDays()
                : jwtProperties.getRefreshTokenStaffExpirationDays();
        int maxAgeSeconds = expiryDays * 24 * 60 * 60;

        String newRawToken = TokenHashUtil.generateRandomToken();
        RefreshToken rotatedSession = refreshTokenService.rotateSession(session.getId(), newRawToken, expiryDays);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole(), rotatedSession.getId());

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .build();

        return AuthResult.builder()
                .authResponse(authResponse)
                .rawRefreshToken(newRawToken)
                .maxAgeSeconds(maxAgeSeconds)
                .build();
    }

    @Override
    @Transactional
    public void logout(UserPrincipal principal) {
        if (principal != null && principal.getSessionId() != null) {
            refreshTokenService.revokeSession(principal.getSessionId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Long tableId = (user.getTable() != null) ? user.getTable().getId() : null;

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .tableId(tableId)
                .build();
    }
}
