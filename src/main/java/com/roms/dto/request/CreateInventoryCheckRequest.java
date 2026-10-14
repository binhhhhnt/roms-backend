package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
public class CreateInventoryCheckRequest {

    /**
     * Optional free-text note for the check session.
     */
    @Size(max = 255, message = "Note must not exceed 255 characters")
    private String note;

    /**
     * When the physical count was taken. Defaults to now() if null.
     */
    private LocalDateTime checkedAt;

    /**
     * List of items with discrepancies. An empty list means "no discrepancies found"
     * (confirmation-type check); the check record is still persisted.
     */
    @Valid
    private List<InventoryCheckItemRequest> items;
}
