package com.roms.service.impl;

import com.roms.dto.request.UpdateSettingRequest;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.entity.RestaurantSetting;
import com.roms.mapper.RestaurantSettingMapper;
import com.roms.repository.RestaurantSettingRepository;
import com.roms.service.RestaurantSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantSettingServiceImpl implements RestaurantSettingService {

    private final RestaurantSettingRepository settingRepository;
    private final RestaurantSettingMapper settingMapper;

    @Override
    @Transactional(readOnly = true)
    public RestaurantSettingResponse getSettings() {
        RestaurantSetting setting = getOrCreateSingletonSetting();
        return settingMapper.toResponse(setting);
    }

    @Override
    @Transactional
    public RestaurantSettingResponse updateSettings(UpdateSettingRequest request) {
        RestaurantSetting setting = getOrCreateSingletonSetting();
        setting.setMenuPriceIncludesVat(request.getMenuPriceIncludesVat());
        RestaurantSetting saved = settingRepository.save(setting);
        log.info("Updated restaurant setting: menu_price_includes_vat={}", saved.getMenuPriceIncludesVat());
        return settingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMenuPriceIncludesVat() {
        return getOrCreateSingletonSetting().getMenuPriceIncludesVat();
    }

    private RestaurantSetting getOrCreateSingletonSetting() {
        return settingRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> settingRepository.save(
                        RestaurantSetting.builder()
                                .menuPriceIncludesVat(true)
                                .build()
                ));
    }
}

