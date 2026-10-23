package com.roms.mapper;

import com.roms.dto.response.UserProfileResponse;
import com.roms.dto.response.UserResponse;
import com.roms.entity.User;
import com.roms.enums.ServiceSessionStatus;
import com.roms.enums.UserRole;
import com.roms.repository.ServiceSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Component Mapper chịu trách nhiệm chuyển đổi giữa Entity User và các DTO Response.
 */
@Component
public class UserMapper {

    private final ServiceSessionRepository sessionRepository;

    public UserMapper(@Autowired(required = false) ServiceSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Chuyển đổi User Entity sang UserResponse đầy đủ thông tin:
     * - Kèm tableId, tableCode
     * - tableIsActive: Trạng thái bàn có đang hoạt động không
     * - hasActiveSession: Bàn có phiên phục vụ đang chạy không
     * - tableRealtimeStatus: Trạng thái bàn thực tế (EMPTY, OCCUPIED, OUT_OF_SERVICE)
     * - canDeactivate: Xác định tài khoản có thể khóa được hay không
     */
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        // Lấy thông tin bàn ăn an toàn, phòng ngừa LazyInitializationException khi Entity bị detached
        Long tableId = null;
        String tableCode = null;
        Boolean tableIsActive = null;
        Boolean hasActiveSession = null;
        String tableRealtimeStatus = null;
        Boolean canDeactivate = true;

        try {
            if (user.getTable() != null) {
                tableId = user.getTable().getId();
                tableCode = user.getTable().getTableCode();
                tableIsActive = user.getTable().getIsActive();

                if (tableId != null && sessionRepository != null) {
                    hasActiveSession = sessionRepository.findByTableIdAndStatus(tableId, ServiceSessionStatus.ACTIVE).isPresent();
                } else {
                    hasActiveSession = false;
                }

                if (!Boolean.TRUE.equals(tableIsActive)) {
                    tableRealtimeStatus = "OUT_OF_SERVICE";
                } else if (Boolean.TRUE.equals(hasActiveSession)) {
                    tableRealtimeStatus = "OCCUPIED";
                } else {
                    tableRealtimeStatus = "EMPTY";
                }

                // Đối với tài khoản bàn (TABLE_ACCOUNT):
                // Nếu bàn đang hoạt động hoặc đang có phiên phục vụ -> KHÔNG THỂ KHÓA TÀI KHOẢN
                if (user.getRole() == UserRole.TABLE_ACCOUNT) {
                    canDeactivate = !Boolean.TRUE.equals(tableIsActive) && !Boolean.TRUE.equals(hasActiveSession);
                }
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
                .tableIsActive(tableIsActive)
                .hasActiveSession(hasActiveSession)
                .tableRealtimeStatus(tableRealtimeStatus)
                .canDeactivate(canDeactivate)
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
