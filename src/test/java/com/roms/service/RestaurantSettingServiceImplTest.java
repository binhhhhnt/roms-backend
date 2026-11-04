package com.roms.service;

import com.roms.dto.request.UpdateSettingRequest;
import com.roms.dto.response.RestaurantSettingResponse;
import com.roms.entity.RestaurantSetting;
import com.roms.mapper.RestaurantSettingMapper;
import com.roms.repository.RestaurantSettingRepository;
import com.roms.service.impl.RestaurantSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantSettingServiceImplTest {

    @Mock
    private RestaurantSettingRepository settingRepository;

    private RestaurantSettingService settingService;

    @BeforeEach
    void setUp() {
        settingService = new RestaurantSettingServiceImpl(settingRepository, new RestaurantSettingMapper());
    }

    @Test
    void getSettings_whenExisting_returnsMappedResponse() {
        RestaurantSetting existing = RestaurantSetting.builder()
                .id(1L)
                .menuPriceIncludesVat(true)
                .updatedAt(LocalDateTime.now())
                .build();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        RestaurantSettingResponse response = settingService.getSettings();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMenuPriceIncludesVat()).isTrue();
    }

    @Test
    void getSettings_whenMissing_createsDefaultAndReturns() {
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(settingRepository.save(any(RestaurantSetting.class))).thenAnswer(invocation -> {
            RestaurantSetting s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        RestaurantSettingResponse response = settingService.getSettings();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMenuPriceIncludesVat()).isTrue();
        verify(settingRepository).save(any(RestaurantSetting.class));
    }

    @Test
    void updateSettings_updatesAndReturnsNewValue() {
        RestaurantSetting existing = RestaurantSetting.builder()
                .id(1L)
                .menuPriceIncludesVat(true)
                .build();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(settingRepository.save(any(RestaurantSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSettingRequest request = UpdateSettingRequest.builder()
                .menuPriceIncludesVat(false)
                .build();

        RestaurantSettingResponse response = settingService.updateSettings(request);

        assertThat(response).isNotNull();
        assertThat(response.getMenuPriceIncludesVat()).isFalse();
        verify(settingRepository).save(existing);
    }

    @Test
    void isMenuPriceIncludesVat_returnsCorrectBoolean() {
        RestaurantSetting existing = RestaurantSetting.builder()
                .id(1L)
                .menuPriceIncludesVat(false)
                .build();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        boolean result = settingService.isMenuPriceIncludesVat();

        assertThat(result).isFalse();
    }
}

