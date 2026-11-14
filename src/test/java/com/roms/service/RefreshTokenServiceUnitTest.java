package com.roms.service;

import com.roms.entity.*;
import com.roms.enums.UserRole;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.RefreshTokenRepository;
import com.roms.security.TokenHashUtil;
import com.roms.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTest {
    @Mock RefreshTokenRepository repository;
    RefreshTokenServiceImpl service;
    @BeforeEach void setUp() { service = new RefreshTokenServiceImpl(repository); }

    @Test @DisplayName("Session: table login revokes earlier sessions and persists only the token hash")
    void createTableSession() {
        var user = User.builder().id(1L).role(UserRole.TABLE_ACCOUNT).build();
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        LocalDateTime before = LocalDateTime.now();
        var result = service.createSession(user, "raw-token", "tablet", 90);
        verify(repository).revokeAllByUserId(eq(1L), any(LocalDateTime.class));
        assertThat(result.getTokenHash()).isEqualTo(TokenHashUtil.sha256("raw-token")).isNotEqualTo("raw-token");
        assertThat(result.getDeviceId()).isEqualTo("tablet");
        assertThat(result.getExpiresAt()).isBetween(before.plusDays(90), LocalDateTime.now().plusDays(90));
    }

    @Test @DisplayName("Session: owner login preserves sessions on other devices")
    void createOwnerSession() {
        var user = User.builder().id(1L).role(UserRole.OWNER).build();
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.createSession(user, "raw-token", "browser", 7);
        verify(repository, never()).revokeAllByUserId(anyLong(), any());
        assertThat(result.getTokenHash()).isEqualTo(TokenHashUtil.sha256("raw-token"));
        assertThat(result.getUser()).isSameAs(user);
    }

    @Test @DisplayName("Session rotation: unknown or revoked session is rejected")
    void rotateUnknownSession() {
        when(repository.findByIdAndRevokedAtIsNull(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotateSession(99L,"new",7)).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any());
    }

    @Test @DisplayName("Session rotation: expired session is marked revoked and rejected")
    void rotateExpiredSession() {
        var session = RefreshToken.builder().id(99L).tokenHash("old").expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(repository.findByIdAndRevokedAtIsNull(99L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.rotateSession(99L,"new",7)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session has expired");
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getTokenHash()).isEqualTo("old");
        verify(repository).save(session);
    }

    @Test @DisplayName("Session rotation: replace hash and extend expiration for valid session")
    void rotateValidSession() {
        var session = RefreshToken.builder().id(99L).tokenHash("old").expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(repository.findByIdAndRevokedAtIsNull(99L)).thenReturn(Optional.of(session));
        when(repository.save(session)).thenReturn(session);
        var before = LocalDateTime.now();
        var result = service.rotateSession(99L,"new",7);
        assertThat(result.getTokenHash()).isEqualTo(TokenHashUtil.sha256("new"));
        assertThat(result.getExpiresAt()).isBetween(before.plusDays(7),LocalDateTime.now().plusDays(7));
    }

    @Test @DisplayName("Session logout: revoke only the requested session")
    void revokeSingleSession() {
        service.revokeSession(99L);
        verify(repository).revokeById(eq(99L),any(LocalDateTime.class));
        verify(repository, never()).revokeAllByUserId(anyLong(),any());
    }

    @Test @DisplayName("Session reset: revoke all sessions belonging to the requested user")
    void revokeAllUserSessions() {
        service.revokeAllUserSessions(10L);
        verify(repository).revokeAllByUserId(eq(10L),any(LocalDateTime.class));
        verify(repository, never()).revokeById(anyLong(),any());
    }
}
