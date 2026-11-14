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
                .restaurantName(setting.getRestaurantName())
                .address(setting.getAddress())
                .phone(setting.getPhone())
                .receiptFooter(setting.getReceiptFooter())
                .printReadyTicket(setting.getPrintReadyTicket())
                .billPrinterName(setting.getBillPrinterName())
                .billPaperSize(setting.getBillPaperSize())
                .readyTicketPrinterName(setting.getReadyTicketPrinterName())
                .readyTicketPaperSize(setting.getReadyTicketPaperSize())
                .qrOrderingEnabled(setting.getQrOrderingEnabled())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }
}
