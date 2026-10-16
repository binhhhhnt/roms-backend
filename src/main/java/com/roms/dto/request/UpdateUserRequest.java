package com.roms.dto.request;

import com.roms.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UpdateUserRequest {

    public UpdateUserRequest() {
    }

    public UpdateUserRequest(String fullName, String email, UserRole role, Long tableId) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.tableId = tableId;
    }

    public UpdateUserRequest(String fullName, String email, UserRole role, Long tableId, String password) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.tableId = tableId;
        this.password = password;
    }

    @NotBlank(message = "Full name cannot be blank")
    @Size(max = 255, message = "Full name cannot exceed 255 characters")
    private String fullName;

    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @NotNull(message = "Role cannot be null")
    private UserRole role;

    private Long tableId;

    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;
}
