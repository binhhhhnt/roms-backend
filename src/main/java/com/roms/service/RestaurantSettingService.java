package com.roms.service;

import com.roms.dto.request.UpdateSettingRequest;
import com.roms.dto.response.RestaurantSettingResponse;

public interface RestaurantSettingService {

    RestaurantSettingResponse getSettings();

    RestaurantSettingResponse updateSettings(UpdateSettingRequest request);

    boolean isMenuPriceIncludesVat();
}

