package com.roms.dto.response;

import com.roms.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserProfileResponse {

    public UserProfileResponse() {
    }

    public UserProfileResponse(Long id, String username, String fullName, UserRole role, Long tableId) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.tableId = tableId;
    }

    private Long id;
    private String username;
    private String fullName;
    private UserRole role;
    private Long tableId;
}
