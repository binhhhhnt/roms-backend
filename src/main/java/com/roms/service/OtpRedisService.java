package com.roms.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Dịch vụ giao tiếp với Redis để quản lý mã OTP đặt lại mật khẩu và giới hạn số lần thử.
 * Cấu trúc Redis Key:
 *   otp:{email}:hash     -> Mã băm SHA-256 của mã OTP (kèm thời gian sống TTL 5 phút)
 *   otp:{email}:attempts -> Bộ đếm số lần nhập sai mã OTP (tối đa 5 lần)
 */
public interface OtpRedisService {

    /**
     * Lưu trữ mã băm OTP vào Redis với thời gian sống mặc định (300 giây / 5 phút).
     *
     * @param email     địa chỉ email của người dùng
     * @param hashedOtp mã băm SHA-256 của chuỗi OTP
     */
    void storeOtp(String email, String hashedOtp);

    /**
     * Lưu trữ mã băm OTP vào Redis với thời gian sống tùy chỉnh.
     *
     * @param email     địa chỉ email của người dùng
     * @param hashedOtp mã băm SHA-256 của chuỗi OTP
     * @param ttl       thời lượng sống của key trong Redis
     */
    void storeOtp(String email, String hashedOtp, Duration ttl);

    /**
     * Lấy mã băm OTP đang lưu trong Redis theo email (nếu còn hạn).
     *
     * @param email địa chỉ email
     * @return Optional chứa mã băm OTP, hoặc rỗng nếu đã hết hạn hoặc không tồn tại
     */
    Optional<String> getOtpHash(String email);

    /**
     * Tăng số lần thử sai OTP lên 1 đơn vị (dùng để chống tấn công Brute-force).
     *
     * @param email địa chỉ email
     * @return số lần thử sai hiện tại
     */
    int incrementAttempts(String email);

    /**
     * Lấy số lần thử sai OTP hiện tại của email.
     *
     * @param email địa chỉ email
     * @return số lần thử sai, trả về 0 nếu chưa có lần thử nào
     */
    int getAttempts(String email);

    /**
     * Kiểm tra xem số lần thử sai đã đạt đến giới hạn tối đa chưa (mặc định 5 lần).
     *
     * @param email địa chỉ email
     * @return true nếu đã vượt quá giới hạn cho phép
     */
    boolean isMaxAttemptsReached(String email);

    /**
     * Xóa sạch cả mã OTP và bộ đếm số lần thử trong Redis (dùng sau khi reset thành công).
     *
     * @param email địa chỉ email
     */
    void deleteOtp(String email);

    /**
     * Lấy thời gian sống mặc định của OTP tính theo giây (300 giây).
     *
     * @return số giây TTL
     */
    long getDefaultTtlSeconds();

    /**
     * Lấy số lần thử sai tối đa được phép (mặc định 5 lần).
     *
     * @return số lần tối đa
     */
    int getMaxAttempts();
}
