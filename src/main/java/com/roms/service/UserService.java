package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.*;
import com.roms.dto.response.UserResponse;
import com.roms.enums.UserRole;
import org.springframework.data.domain.Pageable;

/**
 * Interface Service định nghĩa các nghiệp vụ Quản lý người dùng và Tài khoản (Phase 1).
 * Bao gồm các nghiệp vụ:
 * - CRUD người dùng và phân quyền cho 5 vai trò (BE-005)
 * - Quản lý thông tin hồ sơ cá nhân và đổi mật khẩu (ROMS-004 - ROMS-006)
 */
public interface UserService {

    /**
     * Lấy danh sách người dùng có phân trang, lọc theo vai trò, trạng thái và từ khóa tìm kiếm.
     */
    PageResponse<UserResponse> getUsers(UserRole role, Boolean isActive, String search, Pageable pageable);

    /**
     * Xem thông tin chi tiết một người dùng theo ID.
     */
    UserResponse getUserById(Long id);

    /**
     * Tạo mới tài khoản người dùng với 1 trong 5 vai trò và kiểm tra ràng buộc gắn bàn ăn.
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Cập nhật thông tin người dùng (họ tên, email, vai trò, bàn gắn kèm).
     */
    UserResponse updateUser(Long id, UpdateUserRequest request);

    /**
     * Cập nhật trạng thái hoạt động (kích hoạt / vô hiệu hóa). 
     * Tự động thu hồi phiên đăng nhập nếu tài khoản bị vô hiệu hóa.
     */
    UserResponse updateUserStatus(Long id, UpdateUserStatusRequest request);

    /**
     * Lấy thông tin hồ sơ của người dùng hiện tại đang đăng nhập.
     */
    UserResponse getCurrentUserProfile();

    /**
     * Cập nhật thông tin hồ sơ cá nhân (họ tên, email) của người dùng hiện tại.
     */
    UserResponse updateCurrentUserProfile(UpdateProfileRequest request);

    /**
     * Đổi mật khẩu cho người dùng hiện tại và thu hồi tất cả phiên đăng nhập cũ trên mọi thiết bị.
     */
    void changePassword(ChangePasswordRequest request);
}
