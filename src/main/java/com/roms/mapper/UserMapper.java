package com.roms.mapper;

import com.roms.dto.response.UserProfileResponse;
import com.roms.dto.response.UserResponse;
import com.roms.entity.User;
import org.springframework.stereotype.Component;

/**
 * Component Mapper chịu trách nhiệm chuyển đổi giữa Entity User và các DTO Response.
 */
@Component
public class UserMapper {

    /**
     * Chuyển đổi User Entity sang UserResponse đầy đủ thông tin (kèm tableId và tableCode nếu có).
     */
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        // Lấy thông tin bàn ăn an toàn, phòng ngừa LazyInitializationException khi Entity bị detached
        Long tableId = null;
        String tableCode = null;
        try {
            if (user.getTable() != null) {
                tableId = user.getTable().getId();
                tableCode = user.getTable().getTableCode();
            }
        } catch (Exception ignored) {
            // Trường hợp proxy bị detached
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .tableId(tableId)
                .tableCode(tableCode)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Chuyển đổi User Entity sang UserProfileResponse phục vụ các API xem thông tin cá nhân.
     */
    public UserProfileResponse toProfileResponse(User user) {
        if (user == null) {
            return null;
        }

        Long tableId = (user.getTable() != null) ? user.getTable().getId() : null;

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .tableId(tableId)
                .build();
    }
}
