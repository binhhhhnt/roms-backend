package com.roms.service.impl;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.RefreshTokenRepository;
import com.roms.security.TokenHashUtil;
import com.roms.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Triển khai dịch vụ RefreshTokenService: Quản lý phiên làm việc trong cơ sở dữ liệu PostgreSQL.
 * Thực hiện lưu trữ mã băm SHA-256 của Refresh Token, xoay vòng token (Token Rotation)
 * và thu hồi phiên khi người dùng đăng xuất, đổi mật khẩu hoặc bị vô hiệu hóa.
 */
@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Tạo một phiên làm việc (Session) mới cho người dùng.
     * Quy tắc đặc thù:
     * - Đối với TABLE_ACCOUNT (máy tính bảng tại bàn): Luôn chỉ cho phép tối đa 1 phiên hoạt động.
     *   Mọi phiên cũ của tài khoản bàn này sẽ bị thu hồi ngay lập tức để tránh 2 thiết bị cùng nhận diện 1 bàn.
     * - Băm rawToken bằng SHA-256 trước khi lưu vào DB để đảm bảo bảo mật tuyệt đối.
     */
    @Override
    @Transactional
    public RefreshToken createSession(User user, String rawToken, String deviceId, int expiryDays) {
        LocalDateTime now = LocalDateTime.now();

        // Thu hồi toàn bộ phiên trước đó nếu là tài khoản tablet tại bàn
        if (user.getRole() == UserRole.TABLE_ACCOUNT) {
            refreshTokenRepository.revokeAllByUserId(user.getId(), now);
        }

        // Băm token sang SHA-256 để lưu DB
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

    /**
     * Xoay vòng Refresh Token (Token Rotation):
     * - Kiểm tra phiên còn tồn tại và chưa bị thu hồi (`revoked_at IS NULL`).
     * - Kiểm tra thời hạn sống của phiên.
     * - Cập nhật tokenHash mới và gia hạn thời gian sống (Sliding Expiration).
     */
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

        // Cập nhật mã băm của token mới và gia hạn thêm thời gian
        String newHash = TokenHashUtil.sha256(newRawToken);
        session.setTokenHash(newHash);
        session.setExpiresAt(now.plusDays(expiryDays));

        return refreshTokenRepository.save(session);
    }

    /**
     * Thu hồi phiên làm việc theo ID phiên (đặt revoked_at = now).
     */
    @Override
    @Transactional
    public void revokeSession(Long sessionId) {
        refreshTokenRepository.revokeById(sessionId, LocalDateTime.now());
    }

    /**
     * Thu hồi toàn bộ phiên làm việc của một người dùng (đặt revoked_at = now cho tất cả phiên của user).
     */
    @Override
    @Transactional
    public void revokeAllUserSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    /**
     * Tìm kiếm phiên còn hoạt động: ID khớp, chưa bị thu hồi và thời hạn chưa hết.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActiveSession(Long sessionId) {
        return refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId)
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    /**
     * Tìm kiếm phiên theo mã băm SHA-256 của token.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findSessionByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }
}
