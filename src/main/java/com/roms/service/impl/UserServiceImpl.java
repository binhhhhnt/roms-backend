package com.roms.service.impl;

import com.roms.common.PageResponse;
import com.roms.dto.request.*;
import com.roms.dto.response.UserResponse;
import com.roms.entity.RestaurantTable;
import com.roms.entity.User;
import com.roms.enums.ServiceSessionStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.exception.UnauthorizedException;
import com.roms.mapper.UserMapper;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.UserRepository;
import com.roms.security.SecurityUtils;
import com.roms.service.RefreshTokenService;
import com.roms.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ServiceSessionRepository serviceSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository,
                           RestaurantTableRepository restaurantTableRepository,
                           ServiceSessionRepository serviceSessionRepository,
                           RefreshTokenService refreshTokenService,
                           PasswordEncoder passwordEncoder,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.serviceSessionRepository = serviceSessionRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * Lấy danh sách người dùng có phân trang và bộ lọc linh hoạt.
     * Hỗ trợ tìm kiếm theo username, fullName, email không phân biệt chữ hoa chữ thường.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(UserRole role, Boolean isActive, String search, Pageable pageable) {
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Page<User> userPage = userRepository.findWithFilters(role, isActive, trimmedSearch, pageable);
        return PageResponse.from(userPage, userMapper::toResponse);
    }

    /**
     * Xem thông tin chi tiết một người dùng theo ID.
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toResponse(user);
    }

    /**
     * Tạo mới người dùng:
     * 1. Kiểm tra tính duy nhất của username và email (nếu có).
     * 2. Kiểm tra ràng buộc vai trò:
     *    - TABLE_ACCOUNT: Bắt buộc chỉ định tableId, bàn phải đang hoạt động và chưa được gán cho tablet khác.
     *    - Các vai trò nhân viên khác: Cấm chỉ định tableId (phải là null).
     * 3. Mã hóa mật khẩu bằng BCrypt và lưu vào DB.
     */
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = request.getUsername().trim();
        // 1. Kiểm tra trùng username
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleException("Username is already taken: " + username, "DUPLICATE_USERNAME");
        }

        // 2. Kiểm tra trùng email (nếu email được cung cấp)
        String email = normalizeEmail(request.getEmail());
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already in use: " + email, "DUPLICATE_EMAIL");
        }

        // 3. Xử lý logic ràng buộc gán bàn ăn theo vai trò
        RestaurantTable table = null;
        if (request.getRole() == UserRole.TABLE_ACCOUNT) {
            // Đối với tài khoản tablet bàn: bắt buộc phải có tableId
            if (request.getTableId() == null) {
                throw new BusinessRuleException("Table ID is required for TABLE_ACCOUNT", "TABLE_ID_REQUIRED");
            }
            table = restaurantTableRepository.findById(request.getTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + request.getTableId()));

            // Bàn gắn kết phải đang ở trạng thái hoạt động (is_active = true)
            if (!Boolean.TRUE.equals(table.getIsActive())) {
                throw new BusinessRuleException("Cannot link account to an inactive table", "TABLE_INACTIVE");
            }

            // Mỗi bàn chỉ được gắn tối đa với 1 tài khoản tablet duy nhất (uq_users_table_id)
            final RestaurantTable foundTable = table;
            userRepository.findByTableId(foundTable.getId()).ifPresent(existingUser -> {
                throw new BusinessRuleException("Table " + foundTable.getTableCode() + " is already linked to user: " + existingUser.getUsername(),
                        "TABLE_ALREADY_ASSIGNED");
            });
        } else {
            // Đối với nhân viên (OWNER, SERVICE_STAFF, KITCHEN_STAFF, INVENTORY_STAFF): không được gán tableId
            if (request.getTableId() != null) {
                throw new BusinessRuleException("Staff accounts cannot be linked to a table", "INVALID_TABLE_ASSIGNMENT");
            }
        }

        // 4. Khởi tạo đối tượng User và mã hóa mật khẩu bằng BCrypt
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .email(email)
                .role(request.getRole())
                .table(table)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Created user id: {}, username: {}, role: {}", savedUser.getId(), savedUser.getUsername(), savedUser.getRole());
        return userMapper.toResponse(savedUser);
    }

    /**
     * Cập nhật thông tin người dùng:
     * - Cập nhật họ tên, email, vai trò và bàn gắn kết.
     * - Giữ nguyên username gốc không thay đổi.
     * - Kiểm tra tính duy nhất của email mới và bàn mới nếu có sự thay đổi.
     */
    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Kiểm tra email mới nếu có thay đổi so với email hiện tại
        String email = normalizeEmail(request.getEmail());
        if (email != null && !email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already in use: " + email, "DUPLICATE_EMAIL");
        }

        // Kiểm tra ràng buộc vai trò và bàn ăn
        RestaurantTable table = null;
        if (request.getRole() == UserRole.TABLE_ACCOUNT) {
            if (request.getTableId() == null) {
                throw new BusinessRuleException("Table ID is required for TABLE_ACCOUNT", "TABLE_ID_REQUIRED");
            }
            table = restaurantTableRepository.findById(request.getTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + request.getTableId()));

            final RestaurantTable foundTable = table;
            Long newTableId = foundTable.getId();
            // Đảm bảo bàn mới chưa bị user khác sử dụng
            userRepository.findByTableId(newTableId)
                    .filter(existingUser -> !existingUser.getId().equals(user.getId()))
                    .ifPresent(existingUser -> {
                        throw new BusinessRuleException("Table " + foundTable.getTableCode() + " is already linked to user: " + existingUser.getUsername(),
                                "TABLE_ALREADY_ASSIGNED");
                    });
        } else {
            if (request.getTableId() != null) {
                throw new BusinessRuleException("Staff accounts cannot be linked to a table", "INVALID_TABLE_ASSIGNMENT");
            }
        }

        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setRole(request.getRole());
        user.setTable(table);

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
            refreshTokenService.revokeAllUserSessions(user.getId());
            log.info("Password updated for user id: {}", user.getId());
        }

        User updatedUser = userRepository.save(user);
        log.info("Updated user id: {}, role: {}", updatedUser.getId(), updatedUser.getRole());
        return userMapper.toResponse(updatedUser);
    }

    /**
     * Kích hoạt / Vô hiệu hóa tài khoản người dùng:
     * - Chặn việc quản trị viên tự vô hiệu hóa tài khoản của chính mình.
     * - Khi vô hiệu hóa (isActive = false): Ngay lập tức thu hồi toàn bộ Refresh Token (Session Revocation)
     *   để người dùng bị ngắt phiên đăng nhập trên tất cả các thiết bị.
     */
    @Override
    @Transactional
    public UserResponse updateUserStatus(Long id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Kiểm tra an toàn: Không cho phép tự khóa tài khoản của chính mình
        Long currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        if (id.equals(currentUserId) && Boolean.FALSE.equals(request.getIsActive())) {
            throw new BusinessRuleException("Cannot deactivate your own account", "CANNOT_DEACTIVATE_SELF");
        }

        // Quy tắc bảo vệ tài khoản bàn (TABLE_ACCOUNT):
        // Chỉ tạm thời chặn khóa tài khoản khi bàn đang có phiên phục vụ hoạt động
        if (user.getRole() == UserRole.TABLE_ACCOUNT && Boolean.FALSE.equals(request.getIsActive())) {
            if (user.getTable() != null) {
                boolean hasActiveSession = serviceSessionRepository.findByTableIdAndStatus(
                        user.getTable().getId(), ServiceSessionStatus.ACTIVE
                ).isPresent();
                if (hasActiveSession) {
                    throw new BusinessRuleException(
                            "Không thể khóa tài khoản khi bàn đang có phiên phục vụ hoạt động.",
                            "TABLE_HAS_ACTIVE_SESSION"
                    );
                }
            }
        }

        user.setIsActive(request.getIsActive());
        User savedUser = userRepository.save(user);

        UserResponse response = userMapper.toResponse(savedUser);

        // Nếu vô hiệu hóa tài khoản: Thu hồi toàn bộ refresh token / phiên làm việc đang hoạt động
        if (Boolean.FALSE.equals(request.getIsActive())) {
            refreshTokenService.revokeAllUserSessions(id);
            log.info("Deactivated user id: {}. All active sessions revoked.", id);
        } else {
            log.info("Activated user id: {}.", id);
        }

        return response;
    }

    /**
     * Xem thông tin hồ sơ của chính người dùng đang đăng nhập (dựa trên JWT Security Context).
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile() {
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        return userMapper.toResponse(user);
    }

    /**
     * Cập nhật thông tin hồ sơ cá nhân (họ tên, email) của chính người dùng đang đăng nhập.
     */
    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(UpdateProfileRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        String email = normalizeEmail(request.getEmail());
        if (email != null && !email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already in use: " + email, "DUPLICATE_EMAIL");
        }

        user.setFullName(request.getFullName().trim());
        user.setEmail(email);

        User savedUser = userRepository.save(user);
        log.info("Updated personal profile for user id: {}", currentUserId);
        return userMapper.toResponse(savedUser);
    }

    /**
     * Đổi mật khẩu cá nhân:
     * 1. Xác thực mật khẩu cũ bằng BCrypt so với DB.
     * 2. Kiểm tra mật khẩu mới không được trùng mật khẩu cũ.
     * 3. Mã hóa và lưu mật khẩu mới.
     * 4. Thu hồi toàn bộ Refresh Token của user trên mọi thiết bị để đảm bảo an toàn tuyệt đối.
     */
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        // 1. Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password does not match", "INVALID_CURRENT_PASSWORD");
        }

        // 2. Đảm bảo mật khẩu mới khác mật khẩu cũ
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password cannot be identical to current password", "PASSWORD_UNCHANGED");
        }

        // 3. Cập nhật hash mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 4. Thu hồi tất cả các phiên đăng nhập cũ trên mọi thiết bị
        refreshTokenService.revokeAllUserSessions(currentUserId);
        log.info("Changed password for user id: {}. All active sessions revoked.", currentUserId);
    }

    /**
     * Chuẩn hóa email: loại bỏ khoảng trắng thừa và chuyển về chữ thường.
     */
    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            return null;
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
