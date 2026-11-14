package com.roms.service;

import com.roms.dto.request.*;
import com.roms.entity.*;
import com.roms.enums.UserRole;
import com.roms.exception.*;
import com.roms.repository.UserRepository;
import com.roms.security.*;
import com.roms.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated authentication tests: no Spring context, database, Redis or email server. */
@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {
    @Mock UserRepository users;
    @Mock RefreshTokenService sessions;
    @Mock JwtTokenProvider jwt;
    @Mock PasswordEncoder encoder;
    @Mock OtpRedisService otp;
    @Mock EmailService email;
    AuthServiceImpl service;
    User user;
    JwtProperties properties;

    @BeforeEach void setUp() {
        properties = new JwtProperties();
        service = new AuthServiceImpl(users, sessions, jwt, encoder, properties, otp, email);
        user = User.builder().id(10L).username("staff").passwordHash("encoded")
                .role(UserRole.OWNER).isActive(true).build();
    }

    @Test @DisplayName("Login: reject unknown username without creating a session")
    void loginUnknownUser() {
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "wrong", null), null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid username or password");
        verifyNoInteractions(sessions, jwt);
    }

    @Test @DisplayName("Login: reject wrong password without creating a session")
    void loginWrongPassword() {
        when(users.findByUsername("staff")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "encoded")).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("staff", "wrong", null), null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid username or password");
        verifyNoInteractions(sessions, jwt);
    }

    @Test @DisplayName("Login: reject inactive account even with correct password")
    void loginInactiveAccount() {
        user.setIsActive(false);
        when(users.findByUsername("staff")).thenReturn(Optional.of(user));
        when(encoder.matches("correct", "encoded")).thenReturn(true);
        assertThatThrownBy(() -> service.login(new LoginRequest("staff", "correct", null), null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Account is inactive");
        verifyNoInteractions(sessions, jwt);
    }

    @Test @DisplayName("Login: owner receives Bearer token and seven-day refresh cookie")
    void loginOwner() {
        when(users.findByUsername("staff")).thenReturn(Optional.of(user));
        when(encoder.matches("correct", "encoded")).thenReturn(true);
        when(sessions.createSession(eq(user), anyString(), eq("browser"), eq(7)))
                .thenReturn(RefreshToken.builder().id(99L).build());
        when(jwt.generateAccessToken(10L, UserRole.OWNER, 99L)).thenReturn("access");
        var result = service.login(new LoginRequest("staff", "correct", null), "browser");
        assertThat(result.getAuthResponse().getAccessToken()).isEqualTo("access");
        assertThat(result.getAuthResponse().getTokenType()).isEqualTo("Bearer");
        assertThat(result.getAuthResponse().getExpiresIn()).isEqualTo(900);
        assertThat(result.getMaxAgeSeconds()).isEqualTo(604800);
        assertThat(result.getRawRefreshToken()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test @DisplayName("Login: table account receives ninety-day refresh cookie")
    void loginTableAccount() {
        user.setRole(UserRole.TABLE_ACCOUNT);
        when(users.findByUsername("staff")).thenReturn(Optional.of(user));
        when(encoder.matches("correct", "encoded")).thenReturn(true);
        when(sessions.createSession(eq(user), anyString(), eq("tablet"), eq(90)))
                .thenReturn(RefreshToken.builder().id(99L).build());
        var result = service.login(new LoginRequest("staff", "correct", null), "tablet");
        assertThat(result.getMaxAgeSeconds()).isEqualTo(7776000);
        verify(jwt).generateAccessToken(10L, UserRole.TABLE_ACCOUNT, 99L);
    }

    @Test @DisplayName("Refresh: reject missing cookie")
    void refreshNullToken() {
        assertThatThrownBy(() -> service.refresh(null, null)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(sessions, jwt);
    }

    @Test @DisplayName("Refresh: reject whitespace-only cookie")
    void refreshBlankToken() {
        assertThatThrownBy(() -> service.refresh("  ", null)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(sessions, jwt);
    }

    @Test @DisplayName("Refresh: reject unknown token hash")
    void refreshUnknownToken() {
        when(sessions.findSessionByTokenHash(TokenHashUtil.sha256("old"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("old", null)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
        verify(sessions, never()).rotateSession(anyLong(), anyString(), anyInt());
    }

    @Test @DisplayName("Refresh: reject revoked session")
    void refreshRevokedSession() {
        var session = RefreshToken.builder().id(99L).user(user).revokedAt(LocalDateTime.now()).build();
        when(sessions.findSessionByTokenHash(TokenHashUtil.sha256("old"))).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.refresh("old", null)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session has been revoked");
        verify(sessions, never()).rotateSession(anyLong(), anyString(), anyInt());
    }

    @Test @DisplayName("Refresh: reject expired session")
    void refreshExpiredSession() {
        var session = RefreshToken.builder().id(99L).user(user).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(sessions.findSessionByTokenHash(TokenHashUtil.sha256("old"))).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.refresh("old", null)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session has expired");
        verify(sessions, never()).rotateSession(anyLong(), anyString(), anyInt());
    }

    @Test @DisplayName("Refresh: reject inactive user without rotating session")
    void refreshInactiveUser() {
        user.setIsActive(false);
        var session = RefreshToken.builder().id(99L).user(user).expiresAt(LocalDateTime.now().plusDays(1)).build();
        when(sessions.findSessionByTokenHash(TokenHashUtil.sha256("old"))).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.refresh("old", null)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is inactive");
        verify(sessions, never()).rotateSession(anyLong(), anyString(), anyInt());
    }

    @Test @DisplayName("Refresh: trim cookie, rotate token and issue access token")
    void refreshValidSession() {
        var session = RefreshToken.builder().id(99L).user(user).expiresAt(LocalDateTime.now().plusDays(1)).build();
        when(sessions.findSessionByTokenHash(TokenHashUtil.sha256("old"))).thenReturn(Optional.of(session));
        when(sessions.rotateSession(eq(99L), anyString(), eq(7))).thenReturn(session);
        when(jwt.generateAccessToken(10L, UserRole.OWNER, 99L)).thenReturn("new-access");
        var result = service.refresh(" old ", "browser");
        assertThat(result.getAuthResponse().getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRawRefreshToken()).isNotEqualTo("old");
        assertThat(result.getMaxAgeSeconds()).isEqualTo(604800);
    }

    @Test @DisplayName("Logout: null principal performs no session operation")
    void logoutNullPrincipal() {
        service.logout(null);
        verifyNoInteractions(sessions);
    }

    @Test @DisplayName("Forgot password: unknown email sends no OTP")
    void forgotUnknownEmail() {
        var request = new ForgotPasswordRequest(); request.setEmail(" Unknown@ROMS.com ");
        when(users.findByEmail("unknown@roms.com")).thenReturn(Optional.empty());
        service.forgotPassword(request);
        verifyNoInteractions(otp, email);
    }

    @Test @DisplayName("Forgot password: inactive account sends no OTP")
    void forgotInactiveAccount() {
        user.setIsActive(false);
        var request = new ForgotPasswordRequest(); request.setEmail("staff@roms.com");
        when(users.findByEmail("staff@roms.com")).thenReturn(Optional.of(user));
        service.forgotPassword(request);
        verifyNoInteractions(otp, email);
    }

    @Test @DisplayName("Forgot password: normalize email and store hash of the emailed six-digit OTP")
    void forgotActiveAccount() {
        var request = new ForgotPasswordRequest(); request.setEmail(" Staff@ROMS.com ");
        when(users.findByEmail("staff@roms.com")).thenReturn(Optional.of(user));
        service.forgotPassword(request);
        var raw = ArgumentCaptor.forClass(String.class);
        verify(email).sendOtpEmail(eq("staff@roms.com"), raw.capture());
        assertThat(raw.getValue()).matches("[0-9]{6}");
        verify(otp).storeOtp("staff@roms.com", TokenHashUtil.sha256(raw.getValue()));
    }

    @Test @DisplayName("Verify OTP: blocked account cannot read OTP hash")
    void verifyOtpMaxAttempts() {
        var request = new VerifyOtpRequest(); request.setEmail("staff@roms.com"); request.setOtp("123456");
        when(otp.isMaxAttemptsReached("staff@roms.com")).thenReturn(true);
        assertThatThrownBy(() -> service.verifyOtp(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Maximum verification attempts exceeded. Please request a new OTP.");
        verify(otp, never()).getOtpHash(anyString());
    }

    @Test @DisplayName("Verify OTP: expired OTP is rejected")
    void verifyOtpExpired() {
        var request = new VerifyOtpRequest(); request.setEmail("staff@roms.com"); request.setOtp("123456");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyOtp(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("OTP has expired or does not exist. Please request a new OTP.");
    }

    @Test @DisplayName("Verify OTP: wrong code increments attempt counter")
    void verifyOtpWrongCode() {
        var request = new VerifyOtpRequest(); request.setEmail("staff@roms.com"); request.setOtp("000000");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.of(TokenHashUtil.sha256("123456")));
        when(otp.incrementAttempts("staff@roms.com")).thenReturn(2);
        when(otp.getMaxAttempts()).thenReturn(5);
        assertThatThrownBy(() -> service.verifyOtp(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid OTP. Failed attempts: 2/5");
        verify(otp).incrementAttempts("staff@roms.com");
    }

    @Test @DisplayName("Verify OTP: correct trimmed code does not increment attempts")
    void verifyOtpCorrectCode() {
        var request = new VerifyOtpRequest(); request.setEmail(" Staff@ROMS.com "); request.setOtp(" 123456 ");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.of(TokenHashUtil.sha256("123456")));
        service.verifyOtp(request);
        verify(otp, never()).incrementAttempts(anyString());
    }

    @Test @DisplayName("Reset password: save encoded password, delete OTP and revoke every session")
    void resetPasswordSuccess() {
        var request = new ResetPasswordRequest(); request.setEmail(" Staff@ROMS.com ");
        request.setOtp("123456"); request.setNewPassword("New-password-123!");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.of(TokenHashUtil.sha256("123456")));
        when(users.findByEmail("staff@roms.com")).thenReturn(Optional.of(user));
        when(encoder.encode("New-password-123!")).thenReturn("new-encoded");
        service.resetPassword(request);
        assertThat(user.getPasswordHash()).isEqualTo("new-encoded");
        var order = inOrder(users, otp, sessions);
        order.verify(users).saveAndFlush(user);
        order.verify(otp).deleteOtp("staff@roms.com");
        order.verify(sessions).revokeAllUserSessions(10L);
    }

    @Test @DisplayName("Logout: revoke the authenticated session only")
    void logoutAuthenticatedSession() {
        service.logout(UserPrincipal.builder().id(10L).sessionId(99L).build());
        verify(sessions).revokeSession(99L);
        verify(sessions, never()).revokeAllUserSessions(anyLong());
    }

    @Test @DisplayName("Profile: unknown user raises resource-not-found")
    void profileUnknownUser() {
        when(users.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProfile(999L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id: 999");
    }

    @Test @DisplayName("Refresh: table account retains ninety-day session policy")
    void refreshTableAccount() {
        user.setRole(UserRole.TABLE_ACCOUNT);
        var session = RefreshToken.builder().id(99L).user(user).expiresAt(LocalDateTime.now().plusDays(1)).build();
        when(sessions.findSessionByTokenHash(TokenHashUtil.sha256("old"))).thenReturn(Optional.of(session));
        when(sessions.rotateSession(eq(99L), anyString(), eq(90))).thenReturn(session);
        var result = service.refresh("old", "tablet");
        assertThat(result.getMaxAgeSeconds()).isEqualTo(7776000);
        verify(jwt).generateAccessToken(10L, UserRole.TABLE_ACCOUNT, 99L);
    }

    @Test @DisplayName("Reset password: attempt limit prevents password change and OTP consumption")
    void resetPasswordAttemptLimit() {
        var request = new ResetPasswordRequest(); request.setEmail("staff@roms.com");
        request.setOtp("123456"); request.setNewPassword("New-password-123!");
        when(otp.isMaxAttemptsReached("staff@roms.com")).thenReturn(true);
        assertThatThrownBy(() -> service.resetPassword(request)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(users, encoder, sessions);
        verify(otp, never()).getOtpHash(anyString());
        verify(otp, never()).deleteOtp(anyString());
    }

    @Test @DisplayName("Reset password: expired OTP prevents password change")
    void resetPasswordExpiredOtp() {
        var request = new ResetPasswordRequest(); request.setEmail("staff@roms.com");
        request.setOtp("123456"); request.setNewPassword("New-password-123!");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(request)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(users, encoder, sessions);
        verify(otp, never()).deleteOtp(anyString());
    }

    @Test @DisplayName("Reset password: inactive account keeps password and sessions unchanged")
    void resetPasswordInactiveUser() {
        user.setIsActive(false);
        var request = new ResetPasswordRequest(); request.setEmail("staff@roms.com");
        request.setOtp("123456"); request.setNewPassword("New-password-123!");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.of(TokenHashUtil.sha256("123456")));
        when(users.findByEmail("staff@roms.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.resetPassword(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Account is inactive");
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(encoder, sessions);
        verify(otp, never()).deleteOtp(anyString());
    }

    @Test @DisplayName("Reset password: invalid OTP leaves password and sessions unchanged")
    void resetPasswordWrongOtp() {
        var request = new ResetPasswordRequest(); request.setEmail("staff@roms.com");
        request.setOtp("000000"); request.setNewPassword("New-password-123!");
        when(otp.getOtpHash("staff@roms.com")).thenReturn(Optional.of(TokenHashUtil.sha256("123456")));
        assertThatThrownBy(() -> service.resetPassword(request)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(users, encoder, sessions);
        verify(otp, never()).deleteOtp(anyString());
    }
}
