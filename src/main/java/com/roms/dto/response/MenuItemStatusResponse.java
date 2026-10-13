package com.roms.dto.response;

import com.roms.enums.MenuItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemStatusResponse {

    private Long id;
    private String name;
    private MenuItemStatus status;
    private LocalDateTime updatedAt;
}
