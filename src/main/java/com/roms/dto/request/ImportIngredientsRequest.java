package com.roms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ImportIngredientsRequest {
    @NotEmpty
    @Size(max = 500, message = "Chỉ được nhập tối đa 500 nguyên liệu mỗi lần")
    private List<@NotNull @Valid CreateIngredientRequest> ingredients;
}
