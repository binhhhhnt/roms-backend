package com.roms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAreaRequest {

    @NotBlank(message = "Area name cannot be blank")
    @Size(max = 100, message = "Area name must not exceed 100 characters")
    private String name;
}
