package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.dto.request.ForgotPasswordRequest;
import com.roms.dto.request.LoginRequest;
import com.roms.dto.request.ResetPasswordRequest;
import com.roms.dto.request.VerifyOtpRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller phụ trách toàn bộ các luồng Xác thực (Authentication) và Quản lý Mật khẩu.
 * Bao gồm:
 * - Đăng nhập (login): Cấp Access Token và Refresh Token HttpOnly Cookie
 * - Làm mới token (refresh): Xoay vòng Refresh Token (Rotation) để lấy Access Token mới
 * - Đăng xuất (logout): Thu hồi phiên làm việc và xóa cookie
 * - Xem thông tin tài khoản hiện tại (/me)
 * - Quên mật khẩu (forgot-password): Gửi mã OTP 6 số qua email
 * - Xác thực OTP (verify-otp): Kiểm tra mã OTP trong Redis
 * - Đặt lại mật khẩu mới (reset-password): Cập nhật mật khẩu mới và hủy OTP
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Endpoint đăng nhập vào hệ thống.
     * Xác thực username/password, kiểm tra trạng thái hoạt động của tài khoản.
     * Khi thành công:
     * - Trả về Access Token (thời hạn 15 phút) trong phần thân phản hồi (body).
     * - Đặt Refresh Token vào Cookie HttpOnly (thời hạn 7 ngày cho nhân viên, 90 ngày cho bàn).
     *
     * @param request      chứa thông tin username, password, và optional deviceId
     * @param httpRequest  dùng để lấy User-Agent nếu deviceId không được truyền
     * @param httpResponse dùng để thiết lập Set-Cookie header cho Refresh Token
     * @return ApiResponse chứa accessToken và thông tin thời hạn
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // Lấy deviceId từ request, nếu client không truyền thì lấy chuỗi User-Agent của trình duyệt/thiết bị
        String deviceId = request.getDeviceId() != null
                ? request.getDeviceId()
                : httpRequest.getHeader("User-Agent");

        AuthResult result = authService.login(request, deviceId);
        // Thiết lập Cookie HttpOnly bảo mật cao cho Refresh Token
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), result.getMaxAgeSeconds());

        return ResponseEntity.ok(ApiResponse.success("Login successful", result.getAuthResponse()));
    }

    /**
     * Endpoint làm mới Access Token (Token Refresh Rotation).
     * Đọc Refresh Token từ HttpOnly Cookie, kiểm tra trong DB và cấp cặp token mới.
     * Token cũ sẽ bị vô hiệu hóa ngay lập tức để phòng chống tấn công phát lại (Replay Attack).
     *
     * @param refreshTokenCookie giá trị Refresh Token được trích xuất từ Cookie
     * @param httpRequest        dùng để nhận diện thiết bị
     * @param httpResponse       dùng để cập nhật Refresh Token mới vào Cookie
     * @return ApiResponse chứa Access Token mới
     */
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
        // Cập nhật lại Refresh Token mới xoay vòng vào Cookie
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), result.getMaxAgeSeconds());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", result.getAuthResponse()));
    }

    /**
     * Endpoint đăng xuất khỏi hệ thống.
     * Thu hồi phiên làm việc (Session) trong cơ sở dữ liệu và xóa bỏ Cookie trên trình duyệt.
     *
     * @param principal    thông tin người dùng đang đăng nhập từ SecurityContext
     * @param httpResponse dùng để gửi chỉ thị xóa Cookie (maxAge = 0)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse httpResponse) {

        authService.logout(principal);
        // Xóa Cookie trên trình duyệt của client
        clearRefreshTokenCookie(httpResponse);

        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint lấy thông tin profile của người dùng đang đăng nhập.
     *
     * @param principal thông tin UserPrincipal được giải mã từ JWT Access Token
     * @return UserProfileResponse thông tin cơ bản: id, username, fullName, role, tableId
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {

        UserProfileResponse profile = authService.getProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", profile));
    }

    /**
     * Endpoint yêu cầu gửi mã OTP quên mật khẩu qua Email.
     * Áp dụng kỹ thuật Chống dò quét tài khoản (User Enumeration Prevention):
     * Dù email có tồn tại hay không trong hệ thống, API vẫn luôn trả về thông báo thành công chung chung.
     *
     * @param request chứa email cần nhận mã OTP
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("If the email is registered, a password reset OTP has been sent.", null));
    }

    /**
     * Endpoint kiểm tra tính hợp lệ của mã OTP trước khi người dùng nhập mật khẩu mới.
     * Kiểm tra trong Redis (tối đa 5 lần thử sai).
     *
     * @param request chứa email và mã OTP 6 số
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP is valid", null));
    }

    /**
     * Endpoint đặt lại mật khẩu mới bằng mã OTP.
     * Kiểm tra OTP, cập nhật mật khẩu mã hóa BCrypt, xóa OTP trong Redis và hủy mọi session đăng nhập cũ.
     *
     * @param request chứa email, mã OTP và mật khẩu mới
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully. Please login with your new password.", null));
    }

    /**
     * Helper method: Thiết lập Refresh Token vào Cookie với các cờ bảo mật cao:
     * - httpOnly(true): Ngăn mã JavaScript trên client truy cập trực tiếp vào cookie (chống XSS).
     * - secure: Bật HTTPS trong môi trường production.
     * - sameSite(Lax/Strict): Chống tấn công giả mạo yêu cầu qua trang web chéo (CSRF).
     * - path: Giới hạn cookie chỉ gửi đến các endpoint xác thực.
     */
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

    /**
     * Helper method: Chỉ thị cho trình duyệt xóa bỏ Cookie bằng cách đặt maxAge = 0.
     */
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
