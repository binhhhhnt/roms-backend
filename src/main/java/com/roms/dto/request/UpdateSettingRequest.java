package com.roms.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingRequest {

    @NotNull(message = "menu_price_includes_vat cannot be null")
    @JsonProperty("menu_price_includes_vat")
    @JsonAlias({"menuPriceIncludesVat", "menu_price_includes_vat"})
    private Boolean menuPriceIncludesVat;
}
