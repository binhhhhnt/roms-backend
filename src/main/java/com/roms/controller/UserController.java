package com.roms.controller;

import com.roms.common.ApiResponse;
import com.roms.common.PageResponse;
import com.roms.dto.request.*;
import com.roms.dto.response.UserResponse;
import com.roms.enums.UserRole;
import com.roms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * [BE-005] Lấy danh sách người dùng có phân trang, lọc theo vai trò, trạng thái, và tìm kiếm.
     * Quyền truy cập: Chỉ dành cho OWNER (Quản trị viên).
     */
    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {

        Sort sortOrder = Sort.by(Sort.Direction.DESC, "id");
        if (sort.length >= 2) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sort[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortOrder = Sort.by(direction, sort[0]);
        } else if (sort.length == 1) {
            sortOrder = Sort.by(Sort.Direction.ASC, sort[0]);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortOrder);
        PageResponse<UserResponse> users = userService.getUsers(role, isActive, search, pageable);

        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    /**
     * [BE-005] Xem thông tin chi tiết một người dùng theo ID.
     * Quyền truy cập: Chỉ dành cho OWNER.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    /**
     * [BE-005] Tạo tài khoản người dùng mới với vai trò được chỉ định.
     * Áp dụng quy tắc kiểm tra ràng buộc bàn cho TABLE_ACCOUNT và nhân viên.
     * Quyền truy cập: Chỉ dành cho OWNER.
     * Trả về HTTP 201 Created khi thành công.
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", user));
    }

    /**
     * [BE-005] Cập nhật thông tin người dùng (họ tên, email, vai trò, bàn).
     * Quyền truy cập: Chỉ dành cho OWNER.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    /**
     * [BE-005] Bật hoặc tắt trạng thái hoạt động của tài khoản người dùng.
     * Tự động thu hồi mọi phiên đăng nhập (refresh token) nếu tài khoản bị vô hiệu hóa.
     * Quyền truy cập: Chỉ dành cho OWNER.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        UserResponse user = userService.updateUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", user));
    }

    /**
     * [ROMS-004] Xem thông tin hồ sơ của người dùng đang đăng nhập.
     * Quyền truy cập: Mọi người dùng đã xác thực (Bearer Token).
     */
    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUserProfile() {
        UserResponse user = userService.getCurrentUserProfile();
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", user));
    }

    /**
     * [ROMS-005] Cập nhật thông tin hồ sơ cá nhân (họ tên, email) của người dùng đang đăng nhập.
     * Quyền truy cập: Mọi người dùng đã xác thực (Bearer Token).
     */
    @PutMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUserProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse user = userService.updateCurrentUserProfile(request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", user));
    }

    /**
     * [ROMS-006] Đổi mật khẩu cá nhân.
     * Tự động thu hồi tất cả phiên đăng nhập cũ trên mọi thiết bị sau khi đổi thành công.
     * Quyền truy cập: Mọi người dùng đã xác thực (Bearer Token).
     */
    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}
