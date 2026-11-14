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

    @JsonProperty("restaurant_name")
    private String restaurantName;

    @JsonProperty("restaurantName")
    public String getRestaurantNameCamel() {
        return restaurantName;
    }

    @JsonProperty("address")
    private String address;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("receipt_footer")
    private String receiptFooter;

    @JsonProperty("receiptFooter")
    public String getReceiptFooterCamel() {
        return receiptFooter;
    }

    @JsonProperty("print_ready_ticket")
    private Boolean printReadyTicket;

    @JsonProperty("printReadyTicket")
    public Boolean getPrintReadyTicketCamel() {
        return printReadyTicket;
    }

    @JsonProperty("bill_printer_name")
    private String billPrinterName;

    @JsonProperty("billPrinterName")
    public String getBillPrinterNameCamel() {
        return billPrinterName;
    }

    @JsonProperty("bill_paper_size")
    private String billPaperSize;

    @JsonProperty("billPaperSize")
    public String getBillPaperSizeCamel() {
        return billPaperSize;
    }

    @JsonProperty("ready_ticket_printer_name")
    private String readyTicketPrinterName;

    @JsonProperty("readyTicketPrinterName")
    public String getReadyTicketPrinterNameCamel() {
        return readyTicketPrinterName;
    }

    @JsonProperty("ready_ticket_paper_size")
    private String readyTicketPaperSize;

    @JsonProperty("readyTicketPaperSize")
    public String getReadyTicketPaperSizeCamel() {
        return readyTicketPaperSize;
    }

    @JsonProperty("qr_ordering_enabled")
    private Boolean qrOrderingEnabled;

    @JsonProperty("qrOrderingEnabled")
    public Boolean getQrOrderingEnabledCamel() {
        return qrOrderingEnabled;
    }

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("updatedAt")
    public LocalDateTime getUpdatedAtCamel() {
        return updatedAt;
    }
}
