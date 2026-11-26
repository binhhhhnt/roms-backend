package com.roms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UpdateTableRequest {

    public UpdateTableRequest() {
    }

    public UpdateTableRequest(String tableCode, Integer capacity, Long areaId) {
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.areaId = areaId;
    }

    @NotBlank(message = "Table code cannot be blank")
    @Size(max = 20, message = "Table code must not exceed 20 characters")
    private String tableCode;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Area is required")
    private Long areaId;
}
