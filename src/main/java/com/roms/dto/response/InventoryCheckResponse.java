package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCheckResponse {

    private Long id;
    private Long checkedById;
    private String checkedByName;
    private LocalDateTime checkedAt;
    private String note;
    private List<InventoryCheckItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
