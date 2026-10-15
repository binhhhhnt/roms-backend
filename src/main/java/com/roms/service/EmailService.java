package com.roms.service;

/**
 * Dịch vụ gửi thư điện tử (Email) thông báo, bao gồm mã OTP phục hồi mật khẩu.
 */
public interface EmailService {

    /**
     * Gửi email chứa mã OTP đặt lại mật khẩu đến địa chỉ email người dùng.
     *
     * @param toEmail địa chỉ email người nhận
     * @param otp     mã xác thực OTP (6 chữ số)
     */
    void sendOtpEmail(String toEmail, String otp);
}
