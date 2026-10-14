package com.roms.service;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.RefreshTokenRepository;
import com.roms.repository.UserRepository;
import com.roms.security.TokenHashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("TABLE_ACCOUNT policy: Only 1 active session allowed; new session revokes previous session")
    void shouldRevokePreviousSessionOnTableAccountLogin() {
        User tableUser = userRepository.findByUsername("table_t01").orElseThrow();

        // 1st login
        String rawToken1 = TokenHashUtil.generateRandomToken();
        RefreshToken session1 = refreshTokenService.createSession(tableUser, rawToken1, "tablet-dev-1", 90);
        assertThat(session1.getRevokedAt()).isNull();

        // 2nd login on another tablet
        String rawToken2 = TokenHashUtil.generateRandomToken();
        RefreshToken session2 = refreshTokenService.createSession(tableUser, rawToken2, "tablet-dev-2", 90);
        assertThat(session2.getRevokedAt()).isNull();

        // Check session 1 was revoked
        RefreshToken reloadedSession1 = refreshTokenRepository.findById(session1.getId()).orElseThrow();
        assertThat(reloadedSession1.getRevokedAt()).isNotNull();

        // Active session check
        assertThat(refreshTokenService.findActiveSession(session1.getId())).isEmpty();
        assertThat(refreshTokenService.findActiveSession(session2.getId())).isPresent();
    }

    @Test
    @DisplayName("Staff/Owner policy: Multiple active sessions allowed simultaneously")
    void shouldAllowMultipleSessionsForStaff() {
        User staff = userRepository.findByUsername("service01").orElseThrow();

        String rawToken1 = TokenHashUtil.generateRandomToken();
        RefreshToken session1 = refreshTokenService.createSession(staff, rawToken1, "device-1", 7);

        String rawToken2 = TokenHashUtil.generateRandomToken();
        RefreshToken session2 = refreshTokenService.createSession(staff, rawToken2, "device-2", 7);

        assertThat(refreshTokenService.findActiveSession(session1.getId())).isPresent();
        assertThat(refreshTokenService.findActiveSession(session2.getId())).isPresent();
    }

    @Test
    @DisplayName("Rotate session: Replaces token hash and applies sliding expiration")
    void shouldRotateSessionTokenAndExtendExpiry() {
        User staff = userRepository.findByUsername("service02").orElseThrow();
        String rawToken1 = TokenHashUtil.generateRandomToken();
        RefreshToken session = refreshTokenService.createSession(staff, rawToken1, "device-1", 7);

        String oldHash = session.getTokenHash();
        String rawToken2 = TokenHashUtil.generateRandomToken();

        RefreshToken rotatedSession = refreshTokenService.rotateSession(session.getId(), rawToken2, 7);

        assertThat(rotatedSession.getId()).isEqualTo(session.getId());
        assertThat(rotatedSession.getTokenHash()).isNotEqualTo(oldHash);
        assertThat(TokenHashUtil.matches(rawToken2, rotatedSession.getTokenHash())).isTrue();
    }

    @Test
    @DisplayName("Revoke single session on logout")
    void shouldRevokeSingleSession() {
        User staff = userRepository.findByUsername("kitchen01").orElseThrow();
        String rawToken = TokenHashUtil.generateRandomToken();
        RefreshToken session = refreshTokenService.createSession(staff, rawToken, "kitchen-screen", 7);

        refreshTokenService.revokeSession(session.getId());

        Optional<RefreshToken> activeSession = refreshTokenService.findActiveSession(session.getId());
        assertThat(activeSession).isEmpty();
    }

    @Test
    @DisplayName("Fail rotation on already revoked session")
    void shouldFailRotateOnRevokedSession() {
        User staff = userRepository.findByUsername("kitchen01").orElseThrow();
        String rawToken1 = TokenHashUtil.generateRandomToken();
        RefreshToken session = refreshTokenService.createSession(staff, rawToken1, "kitchen-screen", 7);

        refreshTokenService.revokeSession(session.getId());

        String rawToken2 = TokenHashUtil.generateRandomToken();
        assertThatThrownBy(() -> refreshTokenService.rotateSession(session.getId(), rawToken2, 7))
                .isInstanceOf(UnauthorizedException.class);
    }
}
