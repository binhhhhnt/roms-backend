package com.roms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CategoryWriteRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @Size(max = 255)
    private String description;
    @NotNull
    private Integer displayOrder;
    @NotNull
    private Boolean isActive;
}
