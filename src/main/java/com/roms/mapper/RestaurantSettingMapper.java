package com.roms.mapper;

import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.entity.RestaurantSetting;
import org.springframework.stereotype.Component;

@Component
public class RestaurantSettingMapper {

    public RestaurantSettingResponse toResponse(RestaurantSetting setting) {
        if (setting == null) {
            return null;
        }
        return RestaurantSettingResponse.builder()
                .id(setting.getId())
                .menuPriceIncludesVat(setting.getMenuPriceIncludesVat())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }
}

