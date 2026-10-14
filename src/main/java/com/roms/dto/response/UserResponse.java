package com.roms.dto.response;

import com.roms.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserResponse {

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String fullName, String email,
                        UserRole role, Long tableId, String tableCode, Boolean isActive,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.tableId = tableId;
        this.tableCode = tableCode;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private UserRole role;
    private Long tableId;
    private String tableCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
