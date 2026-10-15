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
public class CreateTableRequest {

    public CreateTableRequest() {
    }

    public CreateTableRequest(String tableCode, Integer capacity, String area) {
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.area = area;
    }

    @NotBlank(message = "Table code cannot be blank")
    @Size(max = 20, message = "Table code must not exceed 20 characters")
    private String tableCode;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @Size(max = 100, message = "Area must not exceed 100 characters")
    private String area;
}
