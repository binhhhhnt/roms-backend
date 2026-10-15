package com.roms.service;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;

import java.util.Optional;

/**
 * Interface quản lý phiên làm việc và Refresh Token của người dùng.
 * Đảm bảo chỉ lưu bản băm SHA-256 của token trong cơ sở dữ liệu.
 */
public interface RefreshTokenService {

    /**
     * Tạo phiên làm việc mới cho người dùng và lưu token hash vào database.
     * Đối với TABLE_ACCOUNT: Luôn chỉ duy trì tối đa 1 phiên duy nhất (thu hồi các phiên trước đó).
     *
     * @param user       đối tượng người dùng
     * @param rawToken   chuỗi refresh token thô
     * @param deviceId   định danh thiết bị
     * @param expiryDays số ngày hết hạn (7 ngày cho staff, 90 ngày cho tablet)
     * @return RefreshToken entity đã được lưu
     */
    RefreshToken createSession(User user, String rawToken, String deviceId, int expiryDays);

    /**
     * Xoay vòng phiên làm việc (Token Rotation): Cập nhật token hash mới và gia hạn thời gian sống.
     *
     * @param sessionId   ID của phiên làm việc
     * @param newRawToken chuỗi refresh token mới
     * @param expiryDays  số ngày gia hạn
     * @return RefreshToken entity đã cập nhật
     */
    RefreshToken rotateSession(Long sessionId, String newRawToken, int expiryDays);

    /**
     * Thu hồi (vô hiệu hóa) một phiên làm việc cụ thể.
     *
     * @param sessionId ID của phiên cần thu hồi
     */
    void revokeSession(Long sessionId);

    /**
     * Thu hồi toàn bộ phiên làm việc của một người dùng (dùng khi đổi mật khẩu hoặc khóa tài khoản).
     *
     * @param userId ID người dùng
     */
    void revokeAllUserSessions(Long userId);

    /**
     * Tìm kiếm phiên làm việc còn hiệu lực theo ID phiên.
     *
     * @param sessionId ID của phiên
     * @return Optional RefreshToken nếu phiên còn tồn tại và chưa bị thu hồi
     */
    Optional<RefreshToken> findActiveSession(Long sessionId);

    /**
     * Tìm kiếm phiên làm việc theo chuỗi mã băm SHA-256 của token.
     *
     * @param tokenHash mã băm SHA-256 của token
     * @return Optional RefreshToken
     */
    Optional<RefreshToken> findSessionByTokenHash(String tokenHash);
}
