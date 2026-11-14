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

        if (request.getRestaurantName() != null) {
            setting.setRestaurantName(request.getRestaurantName().trim());
        }
        if (request.getAddress() != null) {
            setting.setAddress(request.getAddress().trim());
        }
        if (request.getPhone() != null) {
            setting.setPhone(request.getPhone().trim());
        }
        if (request.getReceiptFooter() != null) {
            setting.setReceiptFooter(request.getReceiptFooter().trim());
        }
        if (request.getPrintReadyTicket() != null) {
            setting.setPrintReadyTicket(request.getPrintReadyTicket());
        }
        if (request.getBillPrinterName() != null) {
            String val = request.getBillPrinterName().trim();
            setting.setBillPrinterName(val.isEmpty() ? null : val);
        }
        if (request.getBillPaperSize() != null) {
            setting.setBillPaperSize(request.getBillPaperSize());
        }
        if (request.getReadyTicketPrinterName() != null) {
            String val = request.getReadyTicketPrinterName().trim();
            setting.setReadyTicketPrinterName(val.isEmpty() ? null : val);
        }
        if (request.getReadyTicketPaperSize() != null) {
            setting.setReadyTicketPaperSize(request.getReadyTicketPaperSize());
        }
        if (request.getQrOrderingEnabled() != null) {
            setting.setQrOrderingEnabled(request.getQrOrderingEnabled());
        }

        RestaurantSetting saved = settingRepository.save(setting);
        log.info("Updated restaurant setting: id={}, vatInclusive={}, billPrinter={}, readyPrinter={}",
                saved.getId(), saved.getMenuPriceIncludesVat(), saved.getBillPrinterName(), saved.getReadyTicketPrinterName());
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
                                .restaurantName("Nhà hàng ROMS")
                                .address("123 Đường Ẩm Thực, TP. Hồ Chí Minh")
                                .phone("0901 234 567")
                                .receiptFooter("Cảm ơn quý khách và hẹn gặp lại! Wifi: ROMS_VIP / Pass: 88888888")
                                .printReadyTicket(true)
                                .billPaperSize("K80")
                                .readyTicketPaperSize("K80")
                                .qrOrderingEnabled(true)
                                .build()
                ));
    }
}
