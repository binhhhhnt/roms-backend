package com.roms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UpdateProfileRequest {

    public UpdateProfileRequest() {
    }

    public UpdateProfileRequest(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    @NotBlank(message = "Full name cannot be blank")
    @Size(max = 255, message = "Full name cannot exceed 255 characters")
    private String fullName;

    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;
}
