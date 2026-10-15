package com.roms.service;

import com.roms.dto.request.ForgotPasswordRequest;
import com.roms.dto.request.LoginRequest;
import com.roms.dto.request.ResetPasswordRequest;
import com.roms.dto.request.VerifyOtpRequest;
import com.roms.dto.response.AuthResult;
import com.roms.dto.response.UserProfileResponse;
import com.roms.security.UserPrincipal;

/**
 * Interface định nghĩa các dịch vụ Xác thực (Authentication) và Quản lý mật khẩu.
 */
public interface AuthService {

    /**
     * Xác thực thông tin đăng nhập của người dùng.
     * Cấp phát cặp Access Token và Refresh Token.
     *
     * @param request  thông tin username và password
     * @param deviceId định danh thiết bị đăng nhập
     * @return AuthResult chứa AuthResponse (kèm Access Token) và chuỗi raw Refresh Token để set cookie
     */
    AuthResult login(LoginRequest request, String deviceId);

    /**
     * Làm mới token truy cập (Access Token) bằng Refresh Token (Token Rotation).
     *
     * @param rawRefreshToken chuỗi refresh token nhận được từ cookie
     * @param deviceId        định danh thiết bị
     * @return AuthResult chứa token mới và refresh token mới
     */
    AuthResult refresh(String rawRefreshToken, String deviceId);

    /**
     * Đăng xuất người dùng: Thu hồi phiên làm việc hiện tại trong cơ sở dữ liệu.
     *
     * @param principal thông tin người dùng đang đăng nhập
     */
    void logout(UserPrincipal principal);

    /**
     * Lấy thông tin hồ sơ (Profile) của người dùng theo ID.
     *
     * @param userId ID người dùng
     * @return UserProfileResponse thông tin cá nhân
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * Yêu cầu gửi mã OTP quên mật khẩu qua email.
     * Áp dụng chống dò quét tài khoản (User Enumeration Prevention).
     *
     * @param request email nhận mã OTP
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Xác minh tính hợp lệ của mã OTP.
     *
     * @param request email và mã OTP
     */
    void verifyOtp(VerifyOtpRequest request);

    /**
     * Đặt lại mật khẩu mới sau khi xác thực OTP thành công.
     *
     * @param request email, OTP và mật khẩu mới
     */
    void resetPassword(ResetPasswordRequest request);
}
