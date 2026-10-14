package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchUpsertRecipeRequest {

    @NotNull(message = "Items list must not be null")
    @Valid
    private List<UpsertRecipeItemRequest> items;
}
