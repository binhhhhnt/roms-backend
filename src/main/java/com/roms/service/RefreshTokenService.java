package com.roms.service;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;

import java.util.Optional;

public interface RefreshTokenService {

    RefreshToken createSession(User user, String rawToken, String deviceId, int expiryDays);

    RefreshToken rotateSession(Long sessionId, String newRawToken, int expiryDays);

    void revokeSession(Long sessionId);

    void revokeAllUserSessions(Long userId);

    Optional<RefreshToken> findActiveSession(Long sessionId);

    Optional<RefreshToken> findSessionByTokenHash(String tokenHash);
}
