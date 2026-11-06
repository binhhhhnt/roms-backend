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
                .restaurantName("ROMS Central")
                .address("456 Lê Lợi")
                .phone("0987654321")
                .receiptFooter("Hẹn gặp lại")
                .printReadyTicket(true)
                .billPrinterName("POS-80")
                .billPaperSize("K80")
                .readyTicketPrinterName("KITCHEN-58")
                .readyTicketPaperSize("K58")
                .updatedAt(LocalDateTime.now())
                .build();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        RestaurantSettingResponse response = settingService.getSettings();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMenuPriceIncludesVat()).isTrue();
        assertThat(response.getRestaurantName()).isEqualTo("ROMS Central");
        assertThat(response.getAddress()).isEqualTo("456 Lê Lợi");
        assertThat(response.getPhone()).isEqualTo("0987654321");
        assertThat(response.getReceiptFooter()).isEqualTo("Hẹn gặp lại");
        assertThat(response.getPrintReadyTicket()).isTrue();
        assertThat(response.getBillPrinterName()).isEqualTo("POS-80");
        assertThat(response.getBillPaperSize()).isEqualTo("K80");
        assertThat(response.getReadyTicketPrinterName()).isEqualTo("KITCHEN-58");
        assertThat(response.getReadyTicketPaperSize()).isEqualTo("K58");
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
        assertThat(response.getRestaurantName()).isEqualTo("Nhà hàng ROMS");
        assertThat(response.getPrintReadyTicket()).isTrue();
        assertThat(response.getBillPaperSize()).isEqualTo("K80");
        verify(settingRepository).save(any(RestaurantSetting.class));
    }

    @Test
    void updateSettings_updatesAndReturnsNewValue() {
        RestaurantSetting existing = RestaurantSetting.builder()
                .id(1L)
                .menuPriceIncludesVat(true)
                .restaurantName("Old Name")
                .billPaperSize("K80")
                .readyTicketPaperSize("K80")
                .build();
        when(settingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(settingRepository.save(any(RestaurantSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSettingRequest request = UpdateSettingRequest.builder()
                .menuPriceIncludesVat(false)
                .restaurantName("New Restaurant")
                .address("New Address")
                .phone("0123456789")
                .receiptFooter("New Footer")
                .printReadyTicket(false)
                .billPrinterName("Microsoft Print to PDF")
                .billPaperSize("A5")
                .readyTicketPrinterName("POS-80")
                .readyTicketPaperSize("K58")
                .build();

        RestaurantSettingResponse response = settingService.updateSettings(request);

        assertThat(response).isNotNull();
        assertThat(response.getMenuPriceIncludesVat()).isFalse();
        assertThat(response.getRestaurantName()).isEqualTo("New Restaurant");
        assertThat(response.getAddress()).isEqualTo("New Address");
        assertThat(response.getPhone()).isEqualTo("0123456789");
        assertThat(response.getReceiptFooter()).isEqualTo("New Footer");
        assertThat(response.getPrintReadyTicket()).isFalse();
        assertThat(response.getBillPrinterName()).isEqualTo("Microsoft Print to PDF");
        assertThat(response.getBillPaperSize()).isEqualTo("A5");
        assertThat(response.getReadyTicketPrinterName()).isEqualTo("POS-80");
        assertThat(response.getReadyTicketPaperSize()).isEqualTo("K58");
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
