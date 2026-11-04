package com.roms.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSettingResponse {

    private Long id;

    @JsonProperty("menu_price_includes_vat")
    private Boolean menuPriceIncludesVat;

    @JsonProperty("menuPriceIncludesVat")
    public Boolean getMenuPriceIncludesVatCamel() {
        return menuPriceIncludesVat;
    }

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("updatedAt")
    public LocalDateTime getUpdatedAtCamel() {
        return updatedAt;
    }
}
