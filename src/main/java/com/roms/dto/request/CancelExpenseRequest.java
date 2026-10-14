package com.roms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelExpenseRequest {

    @NotBlank(message = "Cancel reason cannot be blank")
    @Size(max = 255, message = "Cancel reason cannot exceed 255 characters")
    private String cancelReason;
}
