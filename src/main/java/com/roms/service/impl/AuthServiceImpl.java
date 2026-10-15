package com.roms.service.impl;

import com.roms.dto.request.ForgotPasswordRequest;
import com.roms.dto.request.LoginRequest;
import com.roms.dto.request.ResetPasswordRequest;
import com.roms.dto.request.VerifyOtpRequest;
import com.roms.dto.response.AuthResponse;
import com.roms.dto.response.AuthResult;
import com.roms.dto.response.UserProfileResponse;
import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.repository.UserRepository;
import com.roms.security.JwtProperties;
import com.roms.security.JwtTokenProvider;
import com.roms.security.TokenHashUtil;
import com.roms.security.UserPrincipal;
import com.roms.service.AuthService;
import com.roms.service.EmailService;
import com.roms.service.OtpRedisService;
import com.roms.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final OtpRedisService otpRedisService;
    private final EmailService emailService;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenService refreshTokenService,
                           JwtTokenProvider jwtTokenProvider,
                           PasswordEncoder passwordEncoder,
                           JwtProperties jwtProperties,
                           OtpRedisService otpRedisService,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.jwtProperties = jwtProperties;
        this.otpRedisService = otpRedisService;
        this.emailService = emailService;
    }

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

    /**
     * [BE-018] Yêu cầu mã OTP quên mật khẩu:
     * 1. Chuẩn hóa email về chữ thường.
     * 2. Nếu email tồn tại và tài khoản đang hoạt động:
     *    - Sinh mã OTP 6 chữ số ngẫu nhiên bằng SecureRandom.
     *    - Băm mã OTP bằng SHA-256 trước khi lưu vào Redis với TTL 5 phút.
     *    - Gửi email thông báo chứa mã OTP gốc.
     * 3. Cơ chế bảo mật: Nếu email không tồn tại, phương thức vẫn kết thúc êm thấm mà không ném lỗi
     *    để phòng chống tấn công dò quét email người dùng (User Enumeration Attack).
     */
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getIsActive())) {
                String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
                String hashedOtp = TokenHashUtil.sha256(otp);
                otpRedisService.storeOtp(normalizedEmail, hashedOtp);
                emailService.sendOtpEmail(normalizedEmail, otp);
                log.info("Password reset OTP generated and dispatched for email: {}", normalizedEmail);
            } else {
                log.warn("Password reset requested for inactive account with email: {}", normalizedEmail);
            }
        });
    }

    /**
     * [BE-019] Kiểm tra mã OTP:
     * 1. Kiểm tra số lần thử sai (Brute-force protection: tối đa 5 lần).
     * 2. Lấy hash OTP trong Redis. Nếu không có hoặc hết hạn 5 phút -> ném lỗi OTP_EXPIRED_OR_INVALID.
     * 3. So khớp mã OTP với hash đã lưu:
     *    - Nếu sai: Tăng số lần thử sai và ném lỗi kèm số lần thử còn lại.
     *    - Nếu đúng: Xác thực thành công.
     */
    @Override
    public void verifyOtp(VerifyOtpRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // Kiểm tra đã vượt quá giới hạn thử sai 5 lần chưa
        if (otpRedisService.isMaxAttemptsReached(normalizedEmail)) {
            throw new BusinessRuleException("Maximum verification attempts exceeded. Please request a new OTP.", "OTP_MAX_ATTEMPTS_EXCEEDED");
        }

        // Lấy hash OTP từ Redis
        String storedHash = otpRedisService.getOtpHash(normalizedEmail)
                .orElseThrow(() -> new BusinessRuleException("OTP has expired or does not exist. Please request a new OTP.", "OTP_EXPIRED_OR_INVALID"));

        // So sánh mã OTP gửi lên với hash đã lưu
        if (!TokenHashUtil.matches(request.getOtp().trim(), storedHash)) {
            int attempts = otpRedisService.incrementAttempts(normalizedEmail);
            throw new BusinessRuleException("Invalid OTP. Failed attempts: " + attempts + "/" + otpRedisService.getMaxAttempts(), "INVALID_OTP");
        }
    }

    /**
     * [BE-019] Đặt lại mật khẩu mới bằng OTP:
     * 1. Xác thực tính hợp lệ của mã OTP trong Redis (chống brute force).
     * 2. Tìm tài khoản người dùng tương ứng với email.
     * 3. Mã hóa mật khẩu mới bằng BCrypt và flush ngay vào database.
     * 4. Xóa mã OTP khỏi Redis.
     * 5. Thu hồi toàn bộ Refresh Token / phiên đăng nhập cũ trên mọi thiết bị để bảo vệ tài khoản.
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // 1. Kiểm tra giới hạn số lần thử
        if (otpRedisService.isMaxAttemptsReached(normalizedEmail)) {
            throw new BusinessRuleException("Maximum verification attempts exceeded. Please request a new OTP.", "OTP_MAX_ATTEMPTS_EXCEEDED");
        }

        // 2. Xác minh OTP từ Redis
        String storedHash = otpRedisService.getOtpHash(normalizedEmail)
                .orElseThrow(() -> new BusinessRuleException("OTP has expired or does not exist. Please request a new OTP.", "OTP_EXPIRED_OR_INVALID"));

        if (!TokenHashUtil.matches(request.getOtp().trim(), storedHash)) {
            int attempts = otpRedisService.incrementAttempts(normalizedEmail);
            throw new BusinessRuleException("Invalid OTP. Failed attempts: " + attempts + "/" + otpRedisService.getMaxAttempts(), "INVALID_OTP");
        }

        // 3. Tìm user và kiểm tra trạng thái hoạt động
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("Account is inactive", "ACCOUNT_INACTIVE");
        }

        // 4. Cập nhật mật khẩu mới và ghi nhận vào cơ sở dữ liệu ngay lập tức
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);

        // 5. Xóa OTP trong Redis và hủy toàn bộ phiên làm việc cũ
        otpRedisService.deleteOtp(normalizedEmail);
        refreshTokenService.revokeAllUserSessions(user.getId());

        log.info("Password successfully reset for user id: {}, all prior sessions revoked", user.getId());
    }
}
