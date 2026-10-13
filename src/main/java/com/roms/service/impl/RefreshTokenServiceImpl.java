package com.roms.service.impl;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.RefreshTokenRepository;
import com.roms.security.TokenHashUtil;
import com.roms.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public RefreshToken createSession(User user, String rawToken, String deviceId, int expiryDays) {
        LocalDateTime now = LocalDateTime.now();

        // Policy: TABLE_ACCOUNT has at most 1 active session. Revoke any prior session immediately.
        if (user.getRole() == UserRole.TABLE_ACCOUNT) {
            refreshTokenRepository.revokeAllByUserId(user.getId(), now);
        }

        String hash = TokenHashUtil.sha256(rawToken);
        LocalDateTime expiresAt = now.plusDays(expiryDays);

        RefreshToken session = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .deviceId(deviceId)
                .expiresAt(expiresAt)
                .build();

        return refreshTokenRepository.save(session);
    }

    @Override
    @Transactional
    public RefreshToken rotateSession(Long sessionId, String newRawToken, int expiryDays) {
        LocalDateTime now = LocalDateTime.now();

        RefreshToken session = refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session is invalid or has been revoked"));

        if (session.getExpiresAt().isBefore(now)) {
            session.setRevokedAt(now);
            refreshTokenRepository.save(session);
            throw new UnauthorizedException("Session has expired");
        }

        // Sliding expiration: rotate token hash and extend expiration on the stable session row
        String newHash = TokenHashUtil.sha256(newRawToken);
        session.setTokenHash(newHash);
        session.setExpiresAt(now.plusDays(expiryDays));

        return refreshTokenRepository.save(session);
    }

    @Override
    @Transactional
    public void revokeSession(Long sessionId) {
        refreshTokenRepository.revokeById(sessionId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void revokeAllUserSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActiveSession(Long sessionId) {
        return refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId)
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findSessionByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }
}
