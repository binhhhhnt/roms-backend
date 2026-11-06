package com.roms.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Size(max = 255, message = "restaurant_name must not exceed 255 characters")
    @JsonProperty("restaurant_name")
    @JsonAlias({"restaurantName", "restaurant_name"})
    private String restaurantName;

    @Size(max = 500, message = "address must not exceed 500 characters")
    @JsonProperty("address")
    private String address;

    @Size(max = 30, message = "phone must not exceed 30 characters")
    @JsonProperty("phone")
    private String phone;

    @Size(max = 500, message = "receipt_footer must not exceed 500 characters")
    @JsonProperty("receipt_footer")
    @JsonAlias({"receiptFooter", "receipt_footer"})
    private String receiptFooter;

    @JsonProperty("print_ready_ticket")
    @JsonAlias({"printReadyTicket", "print_ready_ticket"})
    private Boolean printReadyTicket;

    @Size(max = 255, message = "bill_printer_name must not exceed 255 characters")
    @JsonProperty("bill_printer_name")
    @JsonAlias({"billPrinterName", "bill_printer_name"})
    private String billPrinterName;

    @Pattern(regexp = "^(A5|K80|K58)$", message = "bill_paper_size must be A5, K80, or K58")
    @JsonProperty("bill_paper_size")
    @JsonAlias({"billPaperSize", "bill_paper_size"})
    private String billPaperSize;

    @Size(max = 255, message = "ready_ticket_printer_name must not exceed 255 characters")
    @JsonProperty("ready_ticket_printer_name")
    @JsonAlias({"readyTicketPrinterName", "ready_ticket_printer_name"})
    private String readyTicketPrinterName;

    @Pattern(regexp = "^(A5|K80|K58)$", message = "ready_ticket_paper_size must be A5, K80, or K58")
    @JsonProperty("ready_ticket_paper_size")
    @JsonAlias({"readyTicketPaperSize", "ready_ticket_paper_size"})
    private String readyTicketPaperSize;
}
